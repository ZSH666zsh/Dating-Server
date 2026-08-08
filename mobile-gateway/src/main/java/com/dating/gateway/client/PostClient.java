package com.dating.gateway.client;

import com.dating.zhaoshihang.proto.post.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * post-service gRPC 客户端。
 * 帖子增删查 / 点赞 / 评论 / Feed。
 */
@Slf4j
@Component
public class PostClient {

    @GrpcClient("post-service")
    private PostServiceGrpc.PostServiceBlockingStub postStub;

    /**
     * 发帖。
     */
    public CreatePostResponse createPost(String content, java.util.List<String> imageKeys) {
        try {
            var req = CreatePostRequest.newBuilder()
                    .setContent(content).addAllImageKeys(imageKeys).build();
            return postStub.createPost(req);
        } catch (StatusRuntimeException e) {
            log.warn("createPost RPC failed", e);
            throw e;
        }
    }

    /**
     * 帖子详情。
     */
    public GetPostDetailResponse getPostDetail(Long postId) {
        try {
            var req = GetPostDetailRequest.newBuilder().setPostId(postId).build();
            return postStub.getPostDetail(req);
        } catch (StatusRuntimeException e) {
            log.warn("getPostDetail RPC failed", e);
            throw e;
        }
    }

    /**
     * 用户帖子列表。
     */
    public ListUserPostsResponse listUserPosts(Long userId, int pageSize, long cursor) {
        try {
            var req = ListUserPostsRequest.newBuilder()
                    .setUserId(userId).setPageSize(pageSize).setCursor(cursor).build();
            return postStub.listUserPosts(req);
        } catch (StatusRuntimeException e) {
            log.warn("listUserPosts RPC failed", e);
            throw e;
        }
    }

    /**
     * 点赞/取消。
     */
    public ActionLikeResponse actionLike(Long postId, boolean like) {
        try {
            var req = ActionLikeRequest.newBuilder()
                    .setPostId(postId)
                    .setAction(like ? Action.LIKE : Action.UNLIKE)
                    .build();
            return postStub.actionLike(req);
        } catch (StatusRuntimeException e) {
            log.warn("actionLike RPC failed", e);
            throw e;
        }
    }

    /**
     * 发评论。
     */
    public CreateCommentResponse createComment(Long postId, String content) {
        try {
            var req = CreateCommentRequest.newBuilder()
                    .setPostId(postId).setContent(content).build();
            return postStub.createComment(req);
        } catch (StatusRuntimeException e) {
            log.warn("createComment RPC failed", e);
            throw e;
        }
    }

    /**
     * 推荐 Feed。
     */
    public GetRecommendFeedResponse getRecommendFeed(int pageSize, String cursor) {
        try {
            var req = GetRecommendFeedRequest.newBuilder()
                    .setPageSize(pageSize).setCursor(cursor).build();
            return postStub.getRecommendFeed(req);
        } catch (StatusRuntimeException e) {
            log.warn("getRecommendFeed RPC failed", e);
            throw e;
        }
    }
}
