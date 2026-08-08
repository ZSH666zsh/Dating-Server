package com.dating.post.service;

import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.manager.PostStatManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评论计数刷盘 Job：每 60 秒把 Redis 评论增量合并到 DB。
 *
 * <h3>为什么需要这个 Job？</h3>
 * 跟点赞刷盘（LikeFlushJob）同样的写合并模式。
 * 每次发表/删除评论时，只增减 Redis 增量（INCR/DECR），
 * 不直接 UPDATE post_stats.comment_count。
 * 本 Job 每分钟合并一次，避免热点帖的行锁竞争。
 *
 * @see LikeFlushJob 一样的实现模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentFlushJob {

    private final StringRedisTemplate redisTemplate;
    private final PostStatManager postStatManager;
    private final CacheKeyBuilder cacheKeyBuilder;

    // Lua 脚本：原子取增量并归零
    private static final String LUA_GET_AND_RESET =
            "local v = redis.call('GET', KEYS[1]); " +
            "if v then redis.call('SET', KEYS[1], 0) end; " +
            "return v;";

    private static final DefaultRedisScript<String> GET_AND_RESET_SCRIPT;

    static {
        GET_AND_RESET_SCRIPT = new DefaultRedisScript<>();
        GET_AND_RESET_SCRIPT.setScriptText(LUA_GET_AND_RESET);
        GET_AND_RESET_SCRIPT.setResultType(String.class);
    }

    /**
     * 每分钟刷盘一次
     */
    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "post.commentFlush",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT5S")
    public void flushComments() {
        String updatedSetKey = cacheKeyBuilder.postUpdatedSet();

        // 取最多 100 个待刷盘的 postId
        var members = redisTemplate.opsForSet().distinctRandomMembers(updatedSetKey, 100);
        if (members == null || members.isEmpty()) {
            return;
        }

        int flushed = 0;
        for (String postIdStr : members) {
            try {
                // Lua 原子取走评论增量并归零
                String incrKey = cacheKeyBuilder.postCommentIncr(Long.parseLong(postIdStr));
                String deltaStr = redisTemplate.execute(
                        GET_AND_RESET_SCRIPT,
                        List.of(incrKey)
                );

                if (deltaStr != null && !deltaStr.isEmpty()) {
                    int delta = Integer.parseInt(deltaStr);
                    if (delta != 0) {
                        postStatManager.incrementCommentCount(Long.parseLong(postIdStr), delta);
                        flushed++;
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("CommentFlush parse error: postId={}", postIdStr);
            } catch (Exception e) {
                log.error("CommentFlush error for postId={}", postIdStr, e);
            }
        }

        // 从待刷盘集合移除
        redisTemplate.opsForSet().remove(updatedSetKey, members.toArray());

        if (flushed > 0) {
            log.info("Comment flush completed, processed={}/{}", flushed, members.size());
        }
    }
}
