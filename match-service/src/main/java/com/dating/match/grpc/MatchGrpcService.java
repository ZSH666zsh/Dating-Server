package com.dating.match.grpc;

import com.dating.match.constant.Direction;
import com.dating.match.constant.ErrorCode;
import com.dating.match.constant.Source;
import com.dating.match.entity.LikeRecord;
import com.dating.match.entity.MatchEntity;
import com.dating.match.entity.VisitRecord;
import com.dating.match.exception.BizException;
import com.dating.match.service.*;
import com.dating.zhaoshihang.proto.match.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * gRPC 匹配服务端实现 —— 8 个 RPC。
 * 对应 match.proto MatchService。
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MatchGrpcService extends MatchServiceGrpc.MatchServiceImplBase {

    private final FeedService feedService;
    private final SwipeService swipeService;
    private final MatchService matchService;
    private final QuotaService quotaService;
    private final LikeVisitService likeVisitService;

    // ──────────────────────────────────────────────
    //  Feed
    // ──────────────────────────────────────────────

    @Override
    public void getTodayFeed(GetTodayFeedRequest req, StreamObserver<GetTodayFeedResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var cards = feedService.getTodayFeed(userId, req.getCount());

            var builder = GetTodayFeedResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"));
            for (var card : cards) {
                builder.addCards(Card.newBuilder()
                        .setTargetUserId(card.getTargetUserId())
                        .setTargetUserType(card.getTargetUserType())
                        .setNickname(card.getNickname() != null ? card.getNickname() : "")
                        .setAge(card.getAge())
                        .addAllPhotoKeys(card.getPhotoKeys() != null ? card.getPhotoKeys() : List.of())
                        .setBio(card.getBio() != null ? card.getBio() : "")
                        .setDistanceKm(card.getDistanceKm()));
            }
            resp.onNext(builder.build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(GetTodayFeedResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getTodayFeed error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  划卡
    // ──────────────────────────────────────────────

    @Override
    public void swipe(SwipeRequest req, StreamObserver<SwipeResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            int direction = req.getDirection() == SwipeDirection.RIGHT ? Direction.RIGHT : Direction.LEFT;
            var result = swipeService.swipe(userId, req.getTargetUserId(), direction);

            resp.onNext(SwipeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setMatched(result.matched())
                    .setMatchId(result.matchId() != null ? result.matchId() : 0)
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(SwipeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("swipe error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  Super Hi
    // ──────────────────────────────────────────────

    @Override
    public void superHi(SuperHiRequest req, StreamObserver<SuperHiResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var result = swipeService.superHi(userId, req.getTargetUserId(), req.getClientRequestId());

            resp.onNext(SuperHiResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setMatched(result.matched())
                    .setMatchId(result.matchId() != null ? result.matchId() : 0)
                    .setRemainingSuperHi(result.remainingSuperHi())
                    .setCoinsUsed(result.coinsUsed())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(SuperHiResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("superHi error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  匹配列表
    // ──────────────────────────────────────────────

    @Override
    public void listMatches(ListMatchesRequest req, StreamObserver<ListMatchesResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            long cursor = 0;
            try { cursor = Long.parseLong(req.getPageToken()); } catch (Exception ignored) {}

            var matches = matchService.listMatches(userId, req.getPageSize(), cursor > 0 ? cursor : null);

            var builder = ListMatchesResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"));
            for (var m : matches) {
                long partnerId = m.getUserIdLow().equals(userId) ? m.getUserIdHigh() : m.getUserIdLow();
                builder.addMatches(MatchVO.newBuilder()
                        .setMatchId(m.getId())
                        .setPartnerUserId(partnerId)
                        .setMatchedAtUnixMs(m.getMatchedAt() != null
                                ? m.getMatchedAt().toInstant().toEpochMilli() : 0)
                        .setSource(m.getSource() != null ? m.getSource() : ""));
            }
            if (!matches.isEmpty()) {
                builder.setNextPageToken(String.valueOf(matches.get(matches.size() - 1).getId()));
            }
            resp.onNext(builder.build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ListMatchesResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listMatches error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  配额
    // ──────────────────────────────────────────────

    @Override
    public void getQuota(GetQuotaRequest req, StreamObserver<GetQuotaResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            var quota = quotaService.getQuota(userId);

            resp.onNext(GetQuotaResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setDailyRightSwipeLimit(quota.dailyRightSwipeLimit())
                    .setDailyRightSwipeUsed(quota.dailyRightSwipeUsed())
                    .setDailyCardLimit(quota.dailyCardLimit())
                    .setDailyCardUsed(quota.dailyCardUsed())
                    .setDailySuperHiLimit(quota.dailySuperHiLimit())
                    .setDailySuperHiUsed(quota.dailySuperHiUsed())
                    .setSuperHiCoinPrice(quota.superHiCoinPrice())
                    .setSubscriptionTier(quota.subscriptionTier())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getQuota error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  Like
    // ──────────────────────────────────────────────

    @Override
    public void listLikesOfMe(ListLikesOfMeRequest req, StreamObserver<ListLikesOfMeResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            long cursor = 0;
            try { cursor = Long.parseLong(req.getPageToken()); } catch (Exception ignored) {}

            var likes = likeVisitService.listLikesOfMe(userId, req.getPageSize(), cursor > 0 ? cursor : null);

            var builder = ListLikesOfMeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"));
            for (var like : likes) {
                builder.addLikes(LikeVO.newBuilder()
                        .setFromUserId(like.getFromUserId())
                        .setLikedAtUnixMs(like.getLikedAt() != null
                                ? like.getLikedAt().toInstant().toEpochMilli() : 0)
                        .setLikeContent(like.getLikeContent() != null ? like.getLikeContent() : ""));
            }
            if (!likes.isEmpty()) {
                builder.setNextPageToken(String.valueOf(likes.get(likes.size() - 1).getId()));
            }
            resp.onNext(builder.build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ListLikesOfMeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listLikesOfMe error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ──────────────────────────────────────────────
    //  Visit
    // ──────────────────────────────────────────────

    @Override
    public void listVisitsOfMe(ListVisitsOfMeRequest req, StreamObserver<ListVisitsOfMeResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            long cursor = 0;
            try { cursor = Long.parseLong(req.getPageToken()); } catch (Exception ignored) {}

            var visits = likeVisitService.listVisitsOfMe(userId, req.getPageSize(), cursor > 0 ? cursor : null);

            var builder = ListVisitsOfMeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"));
            for (var visit : visits) {
                builder.addVisits(VisitVO.newBuilder()
                        .setFromUserId(visit.getFromUserId())
                        .setVisitCount(visit.getVisitCount() != null ? visit.getVisitCount() : 0)
                        .setVisitedAtUnixMs(visit.getVisitedAt() != null
                                ? visit.getVisitedAt().toInstant().toEpochMilli() : 0));
            }
            if (!visits.isEmpty()) {
                builder.setNextPageToken(String.valueOf(visits.get(visits.size() - 1).getId()));
            }
            resp.onNext(builder.build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ListVisitsOfMeResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listVisitsOfMe error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void recordVisit(RecordVisitRequest req, StreamObserver<RecordVisitResponse> resp) {
        try {
            Long userId = GrpcServerInterceptor.getCurrentUserId();
            likeVisitService.recordVisit(userId, req.getTargetUserId());

            resp.onNext(RecordVisitResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setOk(true)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("recordVisit error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }
}
