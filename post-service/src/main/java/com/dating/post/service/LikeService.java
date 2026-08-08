package com.dating.post.service;

import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.constant.ErrorCode;
import com.dating.post.constant.LikeStatus;
import com.dating.post.entity.Post;
import com.dating.post.exception.BizException;
import com.dating.post.manager.PostLikeManager;
import com.dating.post.manager.PostManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 点赞 / 取消点赞
 *
 * 写合并模式：DB upsert + Redis 增量累加，由 LikeFlushJob 每分钟刷盘。
 * 实时点赞数 = DB post_stats.like_count + Redis 增量（未刷盘部分）。
 *
 * @see LikeFlushJob
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final PostManager postManager;
    private final PostLikeManager postLikeManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;

    /**
     * 点赞或取消
     *
     * @return 点赞结果（当前是否点赞状态）
     */
    public LikeResult actionLike(Long userId, Long postId, boolean like) {
        // 1. 校验帖子存在
        Post post = postManager.getByPostId(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "帖子不存在");
        }

        // 2. DB upsert（幂等）
        int status = like ? LikeStatus.LIKED : LikeStatus.CANCELLED;
        boolean changed = postLikeManager.upsert(userId, postId, status);

        // 3. 状态真变了 → 更新 Redis 增量 + 标记待刷盘
        if (changed) {
            String incrKey = cacheKeyBuilder.postLikeIncr(postId);
            // Redis INCR/DECR（原子增减，不加锁）
            Long newIncr = redisTemplate.opsForValue().increment(incrKey, like ? 1 : -1);
            // 把 postId 记入待刷盘集合，LikeFlushJob 每分钟扫描这个集合做批量刷盘
            redisTemplate.opsForSet().add(cacheKeyBuilder.postUpdatedSet(), String.valueOf(postId));

            log.info("Like action: userId={} postId={} liked={} incr={}", userId, postId, like, newIncr);
        } else {
            log.debug("Like action ignored (idempotent): userId={} postId={} liked={}", userId, postId, like);
        }

        // 4. 返回当前点赞状态
        boolean finalLiked = postLikeManager.isLiked(userId, postId);
        return new LikeResult(finalLiked);
    }

    public record LikeResult(boolean isLiked) {
    }
}
