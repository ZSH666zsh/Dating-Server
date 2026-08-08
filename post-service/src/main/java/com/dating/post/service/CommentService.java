package com.dating.post.service;

import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.constant.ErrorCode;
import com.dating.post.entity.Post;
import com.dating.post.entity.PostComment;
import com.dating.post.exception.BizException;
import com.dating.post.manager.PostCommentManager;
import com.dating.post.manager.PostManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论业务编排。
 *
 * <h3>Redis ZSet 200 条窗口</h3>
 * 90% 用户只看前几页评论。Redis ZSet 存最近 200 条 comment_id，
 * 挡住绝大多数读流量。翻到 200 条之后才回源 DB。
 *
 * <h3>计数写合并</h3>
 * 发表评论 → INCR Redis 增量 → SADD 待刷盘集合 → CommentFlushJob 每分钟刷盘。
 * 跟点赞计数完全相同的写合并模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final PostManager postManager;
    private final PostCommentManager postCommentManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;

    /** ZSet 窗口上限 */
    private static final int COMMENT_ZSET_MAX = 200;

    /**
     * 发表评论
     */
    public Long createComment(Long userId, Long postId, String content) {
        // 1. 校验帖子存在
        Post post = postManager.getByPostId(postId);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND, "帖子不存在");
        }
        // 2. 校验内容
        if (content == null || content.trim().isEmpty()) {
            throw new BizException(ErrorCode.COMMENT_CONTENT_EMPTY, "评论内容不能为空");
        }
        if (content.length() > 512) {
            throw new BizException(ErrorCode.COMMENT_CONTENT_TOO_LONG, "评论不能超过 512 字符");
        }

        // 3. 生成 comment_id（先用 System.nanoTime，后续换雪花 ID）
        //    TODO: 等 SnowflakeIdGenerator 支持 comment_id 后替换
        Long commentId = System.nanoTime();

        // 4. DB 写入
        PostComment comment = new PostComment();
        comment.setCommentId(commentId);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        comment.setRootId(0L);
        comment.setParentId(0L);
        comment.setReplyToUserId(0L);
        comment.setStatus(1);
        postCommentManager.insert(comment);

        // 5. Redis ZSet：最新 200 条评论窗口
        try {
            String zsetKey = cacheKeyBuilder.postComments(postId);
            redisTemplate.opsForZSet().add(zsetKey, String.valueOf(commentId), (double) commentId);
            // 裁剪到 200 条（移除 score 最低的——即最老的评论）
            Long size = redisTemplate.opsForZSet().zCard(zsetKey);
            if (size != null && size > COMMENT_ZSET_MAX) {
                // 拿到最老的 (size-200) 条，逐条移除
                Set<String> toRemove = redisTemplate.opsForZSet()
                        .range(zsetKey, 0, size - COMMENT_ZSET_MAX - 1);
                if (toRemove != null && !toRemove.isEmpty()) {
                    redisTemplate.opsForZSet().remove(zsetKey, toRemove.toArray(new String[0]));
                }
            }
            redisTemplate.expire(zsetKey, 7, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to update comment ZSet, postId={}", postId, e);
        }

        // 6. Redis 评论计数增量 + 标记待刷盘
        try {
            redisTemplate.opsForValue().increment(cacheKeyBuilder.postCommentIncr(postId));
            redisTemplate.opsForSet().add(cacheKeyBuilder.postUpdatedSet(), String.valueOf(postId));
        } catch (Exception e) {
            log.warn("Failed to increment comment count, postId={}", postId, e);
        }

        log.info("Comment created: commentId={} postId={} userId={}", commentId, postId, userId);
        return commentId;
    }

    /**
     * 评论列表（Redis ZSet 优先 → DB 回源）
     */
    public CommentListResult listComments(Long postId, int pageSize, Long cursor) {
        if (pageSize <= 0 || pageSize > 20) {
            pageSize = 20;
        }

        String zsetKey = cacheKeyBuilder.postComments(postId);

        // ── 尝试从 Redis ZSet 读取（仅覆盖最新 200 条窗口） ──
        if (cursor == null || cursor <= 0) {
            // 第一页：从 ZSet 取前 pageSize 条
            Set<String> commentIdStrs = redisTemplate.opsForZSet()
                    .reverseRange(zsetKey, 0, pageSize - 1);
            if (commentIdStrs != null && !commentIdStrs.isEmpty()) {
                List<Long> commentIds = commentIdStrs.stream()
                        .map(Long::valueOf)
                        .collect(Collectors.toList());
                List<PostComment> comments = commentIds.stream()
                        .map(postCommentManager::getByCommentId)
                        .filter(c -> c != null && c.getDeleted() == 0)
                        .collect(Collectors.toList());
                if (!comments.isEmpty()) {
                    Long nextCursor = comments.get(comments.size() - 1).getCommentId();
                    boolean hasMore = comments.size() >= pageSize;
                    return new CommentListResult(comments, nextCursor, hasMore);
                }
            }
        }

        // ── ZSet 没命中或翻到 200 条之外 → 回源 DB ──
        List<PostComment> comments = postCommentManager.listByPostId(postId, pageSize, cursor);
        if (comments.isEmpty()) {
            return new CommentListResult(Collections.emptyList(), null, false);
        }
        Long nextCursor = comments.get(comments.size() - 1).getCommentId();
        boolean hasMore = comments.size() >= pageSize;
        return new CommentListResult(comments, nextCursor, hasMore);
    }

    /**
     * 删除评论
     */
    public void deleteComment(Long commentId, Long userId) {
        // 1. 权限校验
        if (!postCommentManager.isOwner(commentId, userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }

        // 2. 查评论获取 postId（用于后续清理缓存）
        PostComment comment = postCommentManager.getByCommentId(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND, "评论不存在");
        }
        Long postId = comment.getPostId();

        // 3. DB 软删
        postCommentManager.deleteByCommentId(commentId);

        // 4. Redis ZSet 移除
        try {
            redisTemplate.opsForZSet().remove(cacheKeyBuilder.postComments(postId), String.valueOf(commentId));
        } catch (Exception e) {
            log.warn("Failed to remove from comment ZSet, commentId={}", commentId, e);
        }

        // 5. Redis 评论计数减一 + 标记待刷盘
        try {
            redisTemplate.opsForValue().decrement(cacheKeyBuilder.postCommentIncr(postId));
            redisTemplate.opsForSet().add(cacheKeyBuilder.postUpdatedSet(), String.valueOf(postId));
        } catch (Exception e) {
            log.warn("Failed to decrement comment count, postId={}", postId, e);
        }

        log.info("Comment deleted: commentId={} userId={}", commentId, userId);
    }

    public record CommentListResult(List<PostComment> items, Long nextCursor, boolean hasMore) {
    }
}
