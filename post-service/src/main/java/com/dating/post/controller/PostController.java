package com.dating.post.controller;

import com.dating.post.service.*;
import com.dating.post.vo.PostDetailVO;
import com.dating.post.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 帖子 REST 接口（本机调试用，生产侧由 mobile-gateway 走 gRPC）

 * 所有接口入参 user_id 从请求头 X-User-Id 取 （模拟 gateway 注入的行为）
 */
@Slf4j
@RestController
@RequestMapping("/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final FeedService feedService;

    /** 从请求头取当前用户（模拟 gateway JWT 注入） */
    private Long currentUserId(@RequestHeader("X-User-Id") Long userId) {
        return userId;
    }

    // ─── 帖子 ───

    /**
     * 发布帖子
     * POST /v1/posts
     */
    @PostMapping
    public Result<Map<String, Long>> createPost(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody CreatePostReq req) {
        Long postId = postWriteService.createPost(userId, req.content(), req.imageKeys());
        // 事务提交后，异步处理缓存/冷启动池/写扩散（best-effort，不阻塞返回）
        postWriteService.afterPostCreated(userId, postId, req.content(), req.imageKeys());
        return Result.ok(Map.of("postId", postId));
    }

    /**
     * 帖子详情
     * GET /v1/posts/{postId}
     */
    @GetMapping("/{postId}")
    public Result<PostDetailVO> getPostDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId) {
        PostDetailVO vo = postReadService.getPostDetail(postId, userId);
        return Result.ok(vo);
    }

    /**
     * 删除帖子
     * DELETE /v1/posts/{postId}
     */
    @DeleteMapping("/{postId}")
    public Result<Void> deletePost(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId) {
        postWriteService.deletePost(postId, userId);
        return Result.ok(null);
    }

    /**
     * 用户帖子列表
     * GET /v1/posts/user/{userId}?pageSize=10&cursor=0
     */
    @GetMapping("/user/{targetUserId}")
    public Result<PostReadService.PostListResult> listUserPosts(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long targetUserId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "0") Long cursor) {
        var result = postReadService.listUserPosts(targetUserId, pageSize, cursor);
        return Result.ok(result);
    }

    // ─── 点赞 ───

    /**
     * 点赞
     * POST /v1/posts/{postId}/like
     */
    @PostMapping("/{postId}/like")
    public Result<LikeService.LikeResult> like(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId) {
        var result = likeService.actionLike(userId, postId, true);
        return Result.ok(result);
    }

    /**
     * 取消点赞
     * DELETE /v1/posts/{postId}/like
     */
    @DeleteMapping("/{postId}/like")
    public Result<LikeService.LikeResult> unlike(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId) {
        var result = likeService.actionLike(userId, postId, false);
        return Result.ok(result);
    }

    // ─── 评论 ───

    /**
     * 发表评论
     * POST /v1/posts/{postId}/comment
     */
    @PostMapping("/{postId}/comment")
    public Result<Map<String, Long>> createComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId,
            @RequestBody CreateCommentReq req) {
        Long commentId = commentService.createComment(userId, postId, req.content());
        return Result.ok(Map.of("commentId", commentId));
    }

    /**
     * 评论列表
     * GET /v1/posts/{postId}/comment?pageSize=10&cursor=0
     */
    @GetMapping("/{postId}/comment")
    public Result<CommentService.CommentListResult> listComments(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long postId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "0") Long cursor) {
        var result = commentService.listComments(postId, pageSize, cursor);
        return Result.ok(result);
    }

    /**
     * 删除评论
     * DELETE /v1/posts/comment/{commentId}
     */
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long commentId) {
        commentService.deleteComment(commentId, userId);
        return Result.ok(null);
    }

    // ─── Feed ───

    /**
     * 推荐 Feed
     * GET /v1/posts/feed?pageSize=10&cursor=0:0
     */
    @GetMapping("/feed")
    public Result<FeedService.FeedResult> getFeed(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "0:0") String cursor) {
        var result = feedService.getRecommendFeed(userId, pageSize, cursor);
        return Result.ok(result);
    }

    // ─── 请求体 DTO（内部静态类） ───

    public record CreatePostReq(String content, List<String> imageKeys) {
    }

    public record CreateCommentReq(String content) {
    }
}
