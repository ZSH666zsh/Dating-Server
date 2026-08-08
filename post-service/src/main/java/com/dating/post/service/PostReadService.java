package com.dating.post.service;

import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.constant.ErrorCode;
import com.dating.post.entity.Post;
import com.dating.post.entity.PostImage;
import com.dating.post.entity.PostStat;
import com.dating.post.exception.BizException;
import com.dating.post.manager.PostLikeManager;
import com.dating.post.manager.PostManager;
import com.dating.post.vo.PostDetailVO;
import com.dating.post.vo.PostItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 帖子读取业务编排。
 *
 * <h3>缓存策略</h3>
 * 缓存优先：先查 Redis Hash(post:detail:{postId})，命中则拼上 Redis 增量后返回。
 * 缓存 miss → 回源 DB（3 次单表查：posts + post_images + post_stats）→ 写回缓存。
 *
 * <h3>实时计数</h3>
 * 返回的 likeCount/commentCount = DB 基准值 + Redis 未刷盘增量，永远最新。
 * 即使 LikeFlushJob 还没跑，用户看到的也是实时数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostReadService {

    private final PostManager postManager;
    private final PostLikeManager postLikeManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;

    /** Redis 缓存 TTL：7 天 */
    private static final long CACHE_TTL_DAYS = 7;

    /**
     * 帖子详情（Redis 缓存优先）
     */
    public PostDetailVO getPostDetail(Long postId, Long currentUserId) {
        // ── 1. 尝试 Redis 缓存 ──
        PostDetailVO cached = getFromCache(postId);
        if (cached != null) {
            // 补上 Redis 增量（未刷盘的点赞/评论）
            int likeIncr = getIntFromRedis(cacheKeyBuilder.postLikeIncr(postId));
            int commentIncr = getIntFromRedis(cacheKeyBuilder.postCommentIncr(postId));
            cached.setLikeCount(cached.getLikeCount() + likeIncr);
            cached.setCommentCount(cached.getCommentCount() + commentIncr);

            // 当前用户是否已点赞（实时查 DB，因为点赞状态不缓存）
            if (currentUserId != null) {
                cached.setLiked(postLikeManager.isLiked(currentUserId, postId));
            }
            return cached;
        }

        // ── 2. 缓存 miss → 回源 DB ──
        Post post = postManager.getByPostId(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "帖子不存在");
        }

        List<PostImage> images = postManager.getImagesByPostId(postId);
        PostStat stat = postManager.getStatByPostId(postId);
        boolean isLiked = currentUserId != null && postLikeManager.isLiked(currentUserId, postId);

        // DB 基准值 + Redis 实时增量
        int likeIncr = getIntFromRedis(cacheKeyBuilder.postLikeIncr(postId));
        int commentIncr = getIntFromRedis(cacheKeyBuilder.postCommentIncr(postId));

        PostDetailVO vo = PostDetailVO.builder()
                .postId(post.getPostId())
                .userId(post.getUserId())
                .content(post.getContent())
                .imageKeys(images.stream().map(PostImage::getImageKey).collect(Collectors.toList()))
                .likeCount((stat != null ? stat.getLikeCount() : 0) + likeIncr)
                .commentCount((stat != null ? stat.getCommentCount() : 0) + commentIncr)
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .isLiked(isLiked)
                .build();

        // ── 3. 写回缓存（best-effort） ──
        try {
            writeToCache(vo);
        } catch (Exception e) {
            log.warn("Failed to write post cache, postId={}", postId, e);
        }

        return vo;
    }

    /**
     * 用户帖子列表（游标分页）
     */
    public PostListResult listUserPosts(Long targetUserId, int pageSize, Long cursor) {
        if (pageSize <= 0 || pageSize > 20) {
            pageSize = 20;
        }
        List<Post> posts = postManager.listUserPosts(targetUserId, pageSize, cursor);
        if (posts.isEmpty()) {
            return new PostListResult(Collections.emptyList(), null, false);
        }
        List<PostItemVO> items = posts.stream()
                .map(p -> PostItemVO.builder()
                        .postId(p.getPostId())
                        .content(p.getContent())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        Long nextCursor = posts.get(posts.size() - 1).getPostId();
        boolean hasMore = posts.size() >= pageSize;
        return new PostListResult(items, nextCursor, hasMore);
    }

    // ─── 私有方法：缓存读写 ───

    /** 从 Redis Hash 读取帖子详情 */
    private PostDetailVO getFromCache(Long postId) {
        String key = cacheKeyBuilder.postDetail(postId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        try {
            String imageKeysStr = (String) entries.getOrDefault("imageKeys", "");
            List<String> imageKeys = imageKeysStr.isEmpty()
                    ? Collections.emptyList()
                    : List.of(imageKeysStr.split(","));

            return PostDetailVO.builder()
                    .postId(Long.valueOf((String) entries.get("postId")))
                    .userId(Long.valueOf((String) entries.get("userId")))
                    .content((String) entries.get("content"))
                    .imageKeys(imageKeys)
                    .likeCount(Integer.valueOf((String) entries.getOrDefault("likeCount", "0")))
                    .commentCount(Integer.valueOf((String) entries.getOrDefault("commentCount", "0")))
                    .status(Integer.valueOf((String) entries.getOrDefault("status", "1")))
                    .createdAt(OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(Long.parseLong((String) entries.get("createdAt"))),
                            ZoneOffset.UTC))
                    .updatedAt(null)
                    .isLiked(false)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse cache for postId={}, evicting", postId, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    /** 写入帖子详情到 Redis Hash */
    private void writeToCache(PostDetailVO vo) {
        String key = cacheKeyBuilder.postDetail(vo.getPostId());
        redisTemplate.opsForHash().putAll(key, Map.of(
                "postId", String.valueOf(vo.getPostId()),
                "userId", String.valueOf(vo.getUserId()),
                "content", vo.getContent() != null ? vo.getContent() : "",
                "imageKeys", vo.getImageKeys() != null ? String.join(",", vo.getImageKeys()) : "",
                "likeCount", String.valueOf(vo.getLikeCount()),
                "commentCount", String.valueOf(vo.getCommentCount()),
                "status", String.valueOf(vo.getStatus()),
                "createdAt", String.valueOf(
                        vo.getCreatedAt() != null
                                ? vo.getCreatedAt().toInstant().toEpochMilli()
                                : System.currentTimeMillis())
        ));
        redisTemplate.expire(key, CACHE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }

    /** 安全读取 Redis String 值并转为 int */
    private int getIntFromRedis(String key) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public record PostListResult(List<PostItemVO> items, Long nextCursor, boolean hasMore) {
    }
}
