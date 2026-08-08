package com.dating.user.grpc;

import com.dating.user.constant.ErrorCode;
import com.dating.user.service.RecommendationService;
import com.dating.zhaoshihang.proto.user.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC 推荐服务实现 —— 3 个 RPC。
 *
 * listDhCandidates / nearbyUsers / pickDhCitiesForCaller
 *
 * @see RecommendationService
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationGrpcService extends RecommendationServiceGrpc.RecommendationServiceImplBase {

    private final RecommendationService recommendationService;

    @Override
    public void listDhCandidates(ListDhCandidatesRequest req, StreamObserver<ListDhCandidatesResponse> resp) {
        try {
            var candidates = recommendationService.listDhCandidates(
                    req.getTargetGender(), req.getAgeMin(), req.getAgeMax(),
                    req.getBeautyMin(), req.getBeautyMax(),
                    req.getRacesList(), req.getExcludeUserIdsList(), req.getLimit());
            resp.onNext(ListDhCandidatesResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .addAllCandidates(candidates.stream().map(c -> UserProfileProto.newBuilder()
                            .setUserId(c.getUserId())
                            .setNickname(c.getNickname() != null ? c.getNickname() : "")
                            .setAge(c.getAge() != null ? c.getAge() : 0)
                            .setGender(c.getGender() != null ? c.getGender() : 0)
                            .setBeautyScore(c.getBeautyScore() != null ? c.getBeautyScore() : 0)
                            .setRace(c.getRace() != null ? c.getRace() : "")
                            .setUserType(c.getUserType() != null ? c.getUserType() : 2)
                            .build()).toList())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listDhCandidates error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void nearbyUsers(NearbyUsersRequest req, StreamObserver<NearbyUsersResponse> resp) {
        try {
            var users = recommendationService.nearbyUsers(
                    req.getCallerUserId(), req.getTargetGender(),
                    req.getLastActiveWithinDays(), req.getExcludeUserIdsList(), req.getLimit());
            resp.onNext(NearbyUsersResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .addAllUsers(users.stream().map(u -> UserProfileProto.newBuilder()
                            .setUserId(u.getUserId())
                            .setNickname(u.getNickname() != null ? u.getNickname() : "")
                            .setAge(u.getAge() != null ? u.getAge() : 0)
                            .setGender(u.getGender() != null ? u.getGender() : 0)
                            .setBeautyScore(u.getBeautyScore() != null ? u.getBeautyScore() : 0)
                            .setRace(u.getRace() != null ? u.getRace() : "")
                            .setUserType(1)
                            .build()).toList())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("nearbyUsers error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void pickDhCitiesForCaller(PickDhCitiesForCallerRequest req, StreamObserver<PickDhCitiesForCallerResponse> resp) {
        try {
            var cityMap = recommendationService.pickDhCitiesForCaller(
                    req.getCallerUserId(), req.getDhUserIdsList());
            resp.onNext(PickDhCitiesForCallerResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .putAllDhCityMap(cityMap)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("pickDhCitiesForCaller error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }
}
