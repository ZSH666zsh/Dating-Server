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
 * 点赞刷盘 Job：每 60 秒把 Redis 增量合并到 DB。
 *
 * <h3>为什么需要刷盘？</h3>
 * 高并发场景下（爆款帖 1 秒 1000 人点赞），如果每次点赞都 UPDATE post_stats，
 * PG 行级锁会让 1000 个请求串行排队（第 1000 个用户等 3 秒）。
 * 更糟的是连接池全卡在一行上，发帖/查帖全挂。
 *
 * <h3>写合并模式</h3>
 * 点赞只写 Redis INCR（单线程内存原子操作，~50μs），业务丝滑返回。
 * 本 Job 每分钟跑一次，用 Lua 脚本原子取走 Redis 增量并归零，
 * 合并成 1 次 UPDATE post_stats。1000 次写 → 1 次写。
 *
 * <h3>原子性保证</h3>
 * Lua 脚本内 GET + SET 0 是原子执行，期间不可能有别的 INCR 插入，
 * 不会丢赞。见 post-service-design.md §6.2。
 *
 * @see LikeService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFlushJob {

    private final StringRedisTemplate redisTemplate;
    private final PostStatManager postStatManager;
    private final CacheKeyBuilder cacheKeyBuilder;

    // Lua 脚本：原子取增量并归零（绝不可拆成 GET + SET 两条命令）
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
     * 每分钟刷盘一次。
     * ShedLock 保证多实例部署时只有一个实例执行此 Job。
     */
    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "post.likeFlush",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT5S")
    public void flushLikes() {
        String updatedSetKey = cacheKeyBuilder.postUpdatedSet();

        // 取最多 100 个待刷盘的 postId（随机子集，防单次 Job 耗时过长）
        var members = redisTemplate.opsForSet().distinctRandomMembers(updatedSetKey, 100);
        if (members == null || members.isEmpty()) {
            return;
        }

        int flushed = 0;
        for (String postIdStr : members) {
            try {
                // Lua 脚本：原子取走增量并归零（50 μs）
                String incrKey = cacheKeyBuilder.postLikeIncr(Long.parseLong(postIdStr));
                String deltaStr = redisTemplate.execute(
                        GET_AND_RESET_SCRIPT,
                        List.of(incrKey)   // KEYS[1]
                );

                if (deltaStr != null && !deltaStr.isEmpty()) {
                    int delta = Integer.parseInt(deltaStr);
                    if (delta != 0) {
                        postStatManager.incrementLikeCount(Long.parseLong(postIdStr), delta);
                        flushed++;
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("LikeFlush parse error: postId={}", postIdStr);
            } catch (Exception e) {
                log.error("LikeFlush error for postId={}", postIdStr, e);
            }
        }

        // 从待刷盘集合移除已处理的 postId
        redisTemplate.opsForSet().remove(updatedSetKey, members.toArray());

        if (flushed > 0) {
            log.info("Like flush completed, processed={}/{}", flushed, members.size());
        }
    }
}
