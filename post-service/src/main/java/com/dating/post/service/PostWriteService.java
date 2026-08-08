package com.dating.post.service;

import com.dating.post.client.UserClient;
import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.config.SnowflakeIdGenerator;
import com.dating.post.constant.ErrorCode;
import com.dating.post.constant.PostStatus;
import com.dating.post.entity.Post;
import com.dating.post.exception.BizException;
import com.dating.post.manager.PostManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 发帖 / 删帖 业务编排。
 *
 * <h3>事务边界</h3>
 * {@link #createPost} 的 @Transactional 只覆盖 DB 三表写入（posts + post_images + post_stats）。
 * Redis 缓存/冷启动池/写扩散都在事务外 best-effort 执行：
 * 即使 Redis 挂了，帖子已落库，后续 FeedScoreJob 重建池会兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostWriteService {

    private final PostManager postManager;
    private final SnowflakeIdGenerator.Snowflake snowflake;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final UserClient userClient;
    private final PostFanoutService postFanoutService;

    // Redis 缓存 TTL：7 天
    private static final long CACHE_TTL_DAYS = 7;

    /**
     * 创建帖子。
     *
     * @param userId    发帖人
     * @param content   文本（1~1024 字符）
     * @param imageKeys 图片 key 列表（≤ 9 张）
     * @return postId
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, String content, List<String> imageKeys) {
        // ── 1. 参数校验 ──
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_EMPTY, "内容不能为空");
        }
        if (content.length() > 1024) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG, "内容不能超过 1024 字符");
        }
        if (imageKeys != null && imageKeys.size() > 9) {
            throw new BizException(ErrorCode.IMAGE_COUNT_EXCEEDED, "图片不能超过 9 张");
        }

        // ── 2. 雪花 ID ──
        Long postId = snowflake.nextId();

        // ── 3. 构造实体 ──
        Post post = new Post();
        post.setPostId(postId);
        post.setUserId(userId);
        post.setContent(content.trim());
        post.setStatus(PostStatus.NORMAL);

        // ── 4. 事务写三表（posts + post_images + post_stats） ──
        postManager.createPost(post, imageKeys != null ? imageKeys : List.of());

        log.info("Post created: postId={} userId={} images={}", postId, userId,
                imageKeys != null ? imageKeys.size() : 0);

        return postId;
    }

    /**
     * 事务提交后的后置处理：缓存 + 冷启动池 + 写扩散。
     * 在 Controller/GRPC 调完 {@link #createPost} 后调用。
     * <p>
     * 这些操作都是 best-effort：失败不回滚 DB 事务。
     */
    public void afterPostCreated(Long userId, Long postId, String content, List<String> imageKeys) {
        // ── 5. 写 Redis 帖子详情缓存（HSET，TTL 7天） ──
        try {
            Map<String, String> detailMap = new HashMap<>();
            detailMap.put("postId", String.valueOf(postId));
            detailMap.put("userId", String.valueOf(userId));
            detailMap.put("content", content);
            detailMap.put("imageKeys", imageKeys != null ? String.join(",", imageKeys) : "");
            detailMap.put("likeCount", "0");
            detailMap.put("commentCount", "0");
            detailMap.put("status", String.valueOf(PostStatus.NORMAL));
            detailMap.put("createdAt", String.valueOf(System.currentTimeMillis()));

            redisTemplate.opsForHash().putAll(cacheKeyBuilder.postDetail(postId), detailMap);
            redisTemplate.expire(cacheKeyBuilder.postDetail(postId), CACHE_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to cache post detail, postId={}", postId, e);
        }

        // ── 6. 冷启动池：发帖时同步 ZADD（按性别分桶） ──
        try {
            boolean isMale = userClient.isMale(userId);
            redisTemplate.opsForZSet().add(
                    cacheKeyBuilder.coldStartPool(isMale),
                    String.valueOf(postId),
                    (double) System.currentTimeMillis()
            );
            // TTL 7 天，和池本身一致
            redisTemplate.expire(cacheKeyBuilder.coldStartPool(isMale), CACHE_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to add to cold start pool, postId={}", postId, e);
        }

        // ── 7. @Async 写扩散给关注者（目前是桩，等 user-service 就绪） ──
        postFanoutService.fanoutToFollowers(userId, postId);
    }

    /**
     * 删除帖子（软删）
     */
    public void deletePost(Long postId, Long userId) {
        // 1. 校验存在 + 权限
        Post post = postManager.getByPostId(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能删除自己的帖子");
        }

        // 2. 软删 DB
        postManager.deleteByPostId(postId);

        // 3. 清除 Redis 缓存（best-effort）
        try {
            redisTemplate.delete(cacheKeyBuilder.postDetail(postId));
        } catch (Exception e) {
            log.warn("Failed to delete post cache, postId={}", postId, e);
        }

        // 4. 从冷启动池移除（best-effort，读侧 getPostDetail 抛 404 也可兜底）
        try {
            boolean isMale = userClient.isMale(userId);
            redisTemplate.opsForZSet().remove(cacheKeyBuilder.coldStartPool(isMale), String.valueOf(postId));
        } catch (Exception e) {
            log.warn("Failed to remove from cold start pool, postId={}", postId, e);
        }

        // 5. 从待刷盘集合移除
        try {
            redisTemplate.opsForSet().remove(cacheKeyBuilder.postUpdatedSet(), String.valueOf(postId));
        } catch (Exception e) {
            log.warn("Failed to remove from updated set, postId={}", postId, e);
        }

        log.info("Post deleted: postId={} userId={}", postId, userId);
    }
}
