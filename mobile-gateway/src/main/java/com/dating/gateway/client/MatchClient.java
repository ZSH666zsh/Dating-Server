package com.dating.gateway.client;

import com.dating.zhaoshihang.proto.match.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * match-service gRPC 客户端。
 * 首页划卡 / 配额 / 匹配 / Like-Visit。
 */
@Slf4j
@Component
public class MatchClient {

    @GrpcClient("match-service")
    private MatchServiceGrpc.MatchServiceBlockingStub matchStub;

    /**
     * 获取推荐 Feed 卡片。
     */
    public GetTodayFeedResponse getTodayFeed(int count) {
        try {
            var req = GetTodayFeedRequest.newBuilder().setCount(count).build();
            return matchStub.getTodayFeed(req);
        } catch (StatusRuntimeException e) {
            log.warn("getTodayFeed RPC failed", e);
            throw e;
        }
    }

    /**
     * 划卡（LEFT / RIGHT）。
     */
    public SwipeResponse swipe(Long targetUserId, SwipeDirection direction) {
        try {
            var req = SwipeRequest.newBuilder()
                    .setTargetUserId(targetUserId)
                    .setDirection(direction)
                    .build();
            return matchStub.swipe(req);
        } catch (StatusRuntimeException e) {
            log.warn("swipe RPC failed", e);
            throw e;
        }
    }

    /**
     * Super Hi。
     */
    public SuperHiResponse superHi(Long targetUserId, String clientRequestId) {
        try {
            var req = SuperHiRequest.newBuilder()
                    .setTargetUserId(targetUserId)
                    .setClientRequestId(clientRequestId)
                    .build();
            return matchStub.superHi(req);
        } catch (StatusRuntimeException e) {
            log.warn("superHi RPC failed", e);
            throw e;
        }
    }

    /**
     * 获取匹配列表。
     */
    public ListMatchesResponse listMatches(int pageSize, String pageToken) {
        try {
            var req = ListMatchesRequest.newBuilder()
                    .setPageSize(pageSize).setPageToken(pageToken).build();
            return matchStub.listMatches(req);
        } catch (StatusRuntimeException e) {
            log.warn("listMatches RPC failed", e);
            throw e;
        }
    }

    /**
     * 获取当日配额。
     */
    public GetQuotaResponse getQuota() {
        try {
            return matchStub.getQuota(GetQuotaRequest.newBuilder().build());
        } catch (StatusRuntimeException e) {
            log.warn("getQuota RPC failed", e);
            throw e;
        }
    }

    /**
     * 谁 Like 了我。
     */
    public ListLikesOfMeResponse listLikesOfMe(int pageSize, String pageToken) {
        try {
            var req = ListLikesOfMeRequest.newBuilder()
                    .setPageSize(pageSize).setPageToken(pageToken).build();
            return matchStub.listLikesOfMe(req);
        } catch (StatusRuntimeException e) {
            log.warn("listLikesOfMe RPC failed", e);
            throw e;
        }
    }

    /**
     * 谁访问了我。
     */
    public ListVisitsOfMeResponse listVisitsOfMe(int pageSize, String pageToken) {
        try {
            var req = ListVisitsOfMeRequest.newBuilder()
                    .setPageSize(pageSize).setPageToken(pageToken).build();
            return matchStub.listVisitsOfMe(req);
        } catch (StatusRuntimeException e) {
            log.warn("listVisitsOfMe RPC failed", e);
            throw e;
        }
    }

    /**
     * 上报主页访问。
     */
    public void recordVisit(Long targetUserId) {
        try {
            var req = RecordVisitRequest.newBuilder().setTargetUserId(targetUserId).build();
            matchStub.recordVisit(req);
        } catch (StatusRuntimeException e) {
            log.warn("recordVisit RPC failed", e);
        }
    }
}
