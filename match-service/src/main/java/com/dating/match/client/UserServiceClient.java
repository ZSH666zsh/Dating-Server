package com.dating.match.client;

import com.dating.zhaoshihang.proto.user.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * user-service gRPC 客户端。
 * 对应 match-service-prd-tech.md §7.7 与现有服务的调用。
 *
 * <p>调用 user-service 的 UserProfileService / RecommendationService 获取用户资料和候选。
 */
@Slf4j
@Component
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;

    @GrpcClient("user-service")
    private RecommendationServiceGrpc.RecommendationServiceBlockingStub recommendStub;

    /**
     * 批量获取用户资料。
     */
    public Map<Long, UserProfileProto> batchGetProfile(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        try {
            // 拆分 ≤200 一批
            Map<Long, UserProfileProto> result = new HashMap<>();
            for (int i = 0; i < userIds.size(); i += 200) {
                List<Long> batch = userIds.subList(i, Math.min(i + 200, userIds.size()));
                var req = BatchGetProfileRequest.newBuilder()
                        .addAllUserIds(batch)
                        .build();
                var resp = profileStub.batchGetProfile(req);
                if (resp.getBase().getCode() == 0) {
                    for (var profile : resp.getProfilesList()) {
                        result.put(profile.getUserId(), profile);
                    }
                }
            }
            return result;
        } catch (StatusRuntimeException e) {
            log.warn("UserServiceClient.batchGetProfile failed", e);
            return Map.of();
        }
    }

    /**
     * 获取用户资料。
     */
    public Optional<UserProfileProto> getProfile(Long userId) {
        try {
            var req = GetProfileRequest.newBuilder().setUserId(userId).build();
            var resp = profileStub.getProfile(req);
            if (resp.getBase().getCode() == 0 && resp.hasProfile()) {
                return Optional.of(resp.getProfile());
            }
        } catch (Exception e) {
            log.warn("UserServiceClient.getProfile failed for userId={}", userId, e);
        }
        return Optional.empty();
    }

    /**
     * 获取用户性别。
     * @return true=男 false=女 null=未知
     */
    public Boolean isMale(Long userId) {
        return getProfile(userId)
                .map(p -> p.getGender() == 1)
                .orElse(null);
    }

    /**
     * 获取用户类型。
     * @return 1=BH 2=DH，null 表示获取失败
     */
    public Integer getUserType(Long userId) {
        return getProfile(userId)
                .map(p -> p.getUserType() != 0 ? p.getUserType() : 1)
                .orElse(null);
    }

    /**
     * 获取用户的城市 ID。
     */
    public Long getCityId(Long userId) {
        return getProfile(userId)
                .map(UserProfileProto::getCityId)
                .orElse(null);
    }

    /**
     * DH 候选列表（渐进扩范围）。
     * 对应 match-service-prd-tech.md §4.1 D0 冷启动。
     */
    public List<UserProfileProto> listDhCandidates(int targetGender, int ageMin, int ageMax,
                                                    int beautyMin, int beautyMax,
                                                    List<String> races, List<Long> excludeUserIds, int limit) {
        try {
            var req = ListDhCandidatesRequest.newBuilder()
                    .setTargetGender(targetGender)
                    .setAgeMin(ageMin)
                    .setAgeMax(ageMax)
                    .setBeautyMin(beautyMin)
                    .setBeautyMax(beautyMax)
                    .addAllRaces(races)
                    .addAllExcludeUserIds(excludeUserIds)
                    .setLimit(limit)
                    .build();
            var resp = recommendStub.listDhCandidates(req);
            if (resp.getBase().getCode() == 0) {
                return resp.getCandidatesList();
            }
        } catch (Exception e) {
            log.warn("UserServiceClient.listDhCandidates failed", e);
        }
        return List.of();
    }

    /**
     * 同城 BH 召回。
     * 对应 match-service-prd-tech.md §4.1 nearbyUsers。
     */
    public List<UserProfileProto> nearbyUsers(Long callerUserId, int targetGender,
                                               int lastActiveDays, List<Long> excludeUserIds, int limit) {
        try {
            var req = NearbyUsersRequest.newBuilder()
                    .setCallerUserId(callerUserId)
                    .setTargetGender(targetGender)
                    .setLastActiveWithinDays(lastActiveDays)
                    .addAllExcludeUserIds(excludeUserIds)
                    .setLimit(limit)
                    .build();
            var resp = recommendStub.nearbyUsers(req);
            if (resp.getBase().getCode() == 0) {
                return resp.getUsersList();
            }
        } catch (Exception e) {
            log.warn("UserServiceClient.nearbyUsers failed", e);
        }
        return List.of();
    }

    /**
     * 为 caller 的 DH 候选分配城市（确定性 hash 分配）。
     * 对应 match-service-prd-tech.md §6.3.1 PickDhCitiesForCaller。
     */
    public Map<Long, Long> pickDhCitiesForCaller(Long callerUserId, List<Long> dhUserIds) {
        try {
            var req = PickDhCitiesForCallerRequest.newBuilder()
                    .setCallerUserId(callerUserId)
                    .addAllDhUserIds(dhUserIds)
                    .build();
            var resp = recommendStub.pickDhCitiesForCaller(req);
            if (resp.getBase().getCode() == 0) {
                return resp.getDhCityMapMap();
            }
        } catch (Exception e) {
            log.warn("UserServiceClient.pickDhCitiesForCaller failed", e);
        }
        return Map.of();
    }
}
