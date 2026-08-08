package com.dating.gateway.controller;

import com.dating.gateway.client.PostClient;
import com.dating.gateway.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 帖子 REST 接口（BFF 路由 → post-service gRPC）。
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostClient postClient;

    /**
     * 发帖
     * POST /api/v1/posts
     */
    @PostMapping
    public Result<?> createPost(@RequestBody CreatePostReq req) {
        var resp = postClient.createPost(req.content(), req.imageKeys());
        return Result.ok(Map.of("postId", resp.getPostId()));
    }

    /**
     * 帖子详情
     * GET /api/v1/posts/{postId}
     */
    @GetMapping("/{postId}")
    public Result<?> getPostDetail(@PathVariable Long postId) {
        var resp = postClient.getPostDetail(postId);
        return Result.ok(resp.getPost());
    }

    /**
     * 点赞
     * POST /api/v1/posts/{postId}/like
     */
    @PostMapping("/{postId}/like")
    public Result<?> like(@PathVariable Long postId) {
        var resp = postClient.actionLike(postId, true);
        return Result.ok(Map.of("isLiked", resp.getIsLiked()));
    }

    /**
     * 取消点赞
     * DELETE /api/v1/posts/{postId}/like
     */
    @DeleteMapping("/{postId}/like")
    public Result<?> unlike(@PathVariable Long postId) {
        var resp = postClient.actionLike(postId, false);
        return Result.ok(Map.of("isLiked", resp.getIsLiked()));
    }

    /**
     * 发评论
     * POST /api/v1/posts/{postId}/comment
     */
    @PostMapping("/{postId}/comment")
    public Result<?> createComment(@PathVariable Long postId, @RequestBody CommentReq req) {
        var resp = postClient.createComment(postId, req.content());
        return Result.ok(Map.of("commentId", resp.getCommentId()));
    }

    /**
     * 推荐 Feed
     * GET /api/v1/posts/feed?pageSize=10
     */
    @GetMapping("/feed")
    public Result<?> getFeed(@RequestParam(defaultValue = "10") int pageSize,
                              @RequestParam(defaultValue = "0:0") String cursor) {
        var resp = postClient.getRecommendFeed(pageSize, cursor);
        return Result.ok(Map.of("items", resp.getItemsList(), "nextCursor", resp.getNextCursor()));
    }

    public record CreatePostReq(String content, java.util.List<String> imageKeys) {}
    public record CommentReq(String content) {}
}
