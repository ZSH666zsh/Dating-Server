package com.dating.post.grpc;

import com.dating.post.constant.ErrorCode;
import com.dating.post.exception.BizException;
import com.dating.post.service.*;
import com.dating.zhaoshihang.proto.post.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC 帖子服务端实现 —— 9 个 RPC，对应 proto PostService。
 *
 * <h3>userId 从哪里来？</h3>
 * 生产环境：mobile-gateway 解 JWT 后通过 gRPC Metadata 注入 x-user-id，
 * {@link GrpcServerInterceptor} 提取后放入 Context。
 *
 * 本机调试：可在 gRPC metadata 里传 x-user-id，或 Context 自动取默认值。
 *
 * @see GrpcServerInterceptor
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final FeedService feedService;

    // ──────────────────────────────────────────────
    //  发帖
    // ──────────────────────────────────────────────

    @Override
    public void createPost(CreatePostRequest req, StreamObserver<CreatePostResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            Long postId = postWriteService.createPost(userId, req.getContent(), req.getImageKeysList());
            postWriteService.afterPostCreated(userId, postId, req.getContent(), req.getImageKeysList());
            resp.onNext(CreatePostResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setPostId(postId)
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(CreatePostResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("createPost error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  帖子详情
    // ──────────────────────────────────────────────

    @Override
    public void getPostDetail(GetPostDetailRequest req, StreamObserver<GetPostDetailResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var detail = postReadService.getPostDetail(req.getPostId(), userId);
            resp.onNext(GetPostDetailResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setPost(buildPostInfo(detail))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(GetPostDetailResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getPostDetail error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  用户帖子列表
    // ──────────────────────────────────────────────

    @Override
    public void listUserPosts(ListUserPostsRequest req, StreamObserver<ListUserPostsResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var result = postReadService.listUserPosts(req.getUserId(), req.getPageSize(), req.getCursor());
            resp.onNext(ListUserPostsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .addAllItems(result.items().stream().map(item -> PostInfo.newBuilder()
                            .setPostId(item.getPostId())
                            .setContent(item.getContent() != null ? item.getContent() : "")
                            .build()).toList())
                    .setPagination(Pagination.newBuilder()
                            .setPageSize(req.getPageSize())
                            .setCursor(String.valueOf(result.nextCursor() != null ? result.nextCursor() : 0))
                            .setHasMore(result.hasMore()))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ListUserPostsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listUserPosts error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  点赞 / 取消
    // ──────────────────────────────────────────────

    @Override
    public void actionLike(ActionLikeRequest req, StreamObserver<ActionLikeResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            boolean like = req.getAction() == Action.LIKE;
            var result = likeService.actionLike(userId, req.getPostId(), like);
            resp.onNext(ActionLikeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setIsLiked(result.isLiked())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ActionLikeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("actionLike error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  发表评论
    // ──────────────────────────────────────────────

    @Override
    public void createComment(CreateCommentRequest req, StreamObserver<CreateCommentResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            Long commentId = commentService.createComment(userId, req.getPostId(), req.getContent());
            resp.onNext(CreateCommentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setCommentId(commentId)
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(CreateCommentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("createComment error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  评论列表
    // ──────────────────────────────────────────────

    @Override
    public void listComments(ListCommentsRequest req, StreamObserver<ListCommentsResponse> resp) {
        try {
            var result = commentService.listComments(req.getPostId(), req.getPageSize(), req.getCursor());
            resp.onNext(ListCommentsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .addAllItems(result.items().stream().map(c -> CommentInfo.newBuilder()
                            .setCommentId(c.getCommentId())
                            .setPostId(c.getPostId())
                            .setUserId(c.getUserId())
                            .setContent(c.getContent() != null ? c.getContent() : "")
                            .setRootId(c.getRootId() != null ? c.getRootId() : 0)
                            .setParentId(c.getParentId() != null ? c.getParentId() : 0)
                            .setReplyToUserId(c.getReplyToUserId() != null ? c.getReplyToUserId() : 0)
                            .build()).toList())
                    .setPagination(Pagination.newBuilder()
                            .setPageSize(req.getPageSize())
                            .setCursor(String.valueOf(result.nextCursor() != null ? result.nextCursor() : 0))
                            .setHasMore(result.hasMore()))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ListCommentsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listComments error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  删除评论
    // ──────────────────────────────────────────────

    @Override
    public void deleteComment(DeleteCommentRequest req, StreamObserver<DeleteCommentResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            commentService.deleteComment(req.getCommentId(), userId);
            resp.onNext(DeleteCommentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(DeleteCommentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("deleteComment error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  删除帖子
    // ──────────────────────────────────────────────

    @Override
    public void deletePost(DeletePostRequest req, StreamObserver<DeletePostResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            postWriteService.deletePost(req.getPostId(), userId);
            resp.onNext(DeletePostResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(DeletePostResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("deletePost error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  推荐 Feed
    // ──────────────────────────────────────────────

    @Override
    public void getRecommendFeed(GetRecommendFeedRequest req, StreamObserver<GetRecommendFeedResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var result = feedService.getRecommendFeed(userId, req.getPageSize(), req.getCursor());
            resp.onNext(GetRecommendFeedResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .addAllItems(result.items().stream().map(this::buildPostInfo).toList())
                    .setNextCursor(result.nextCursor() != null ? result.nextCursor() : "0:0")
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(GetRecommendFeedResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getRecommendFeed error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ─── 辅助方法 ───

    private PostInfo buildPostInfo(com.dating.post.vo.PostDetailVO detail) {
        return PostInfo.newBuilder()
                .setPostId(detail.getPostId())
                .setUserId(detail.getUserId())
                .setContent(detail.getContent() != null ? detail.getContent() : "")
                .addAllImageKeys(detail.getImageKeys() != null ? detail.getImageKeys() : java.util.List.of())
                .setLikeCount(detail.getLikeCount() != null ? detail.getLikeCount() : 0)
                .setCommentCount(detail.getCommentCount() != null ? detail.getCommentCount() : 0)
                .setStatus(detail.getStatus() != null ? detail.getStatus() : 0)
                .setIsLiked(detail.isLiked())
                .build();
    }
}
