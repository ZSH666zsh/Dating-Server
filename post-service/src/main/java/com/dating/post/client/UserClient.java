package com.dating.post.client;

import com.dating.zhaoshihang.proto.user.BatchGetProfileRequest;
import com.dating.zhaoshihang.proto.user.GetProfileRequest;
import com.dating.zhaoshihang.proto.user.UserProfileServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * user-service gRPC 客户端 → 调 UserProfileService。
 *
 * <h3>替换历史（学习参考）</h3>
 *
 * <pre>{@code
 * // === 阶段 1：桩实现（UserClient 刚创建时），不桩的话 post-service 编译都过不了===
 * // public boolean isMale(Long userId) {
 * //     return userId % 2 == 0;  // 用取模模拟性别
 * // }
 * // public List<Long> getFriendUserIds(Long userId) {
 * //     return Collections.emptyList();  // 永远无好友
 * // }
 * // public Map<Long, Boolean> getGenders(List<Long> userIds) {
 * //     Map<Long, Boolean> result = new HashMap<>();
 * //     for (Long uid : userIds) result.put(uid, uid % 2 == 0);
 * //     return result;
 * // }
 *
 * // === 阶段 2：REST 过渡（违规方式，仅学习参考）。服务间禁止 RESTFUL HTTP 互调，只能用 gRPC！！！===
 * // 注意：student-dev-guide §10 红线 #3 禁止服务间 HTTP 互调！
 * // private RestTemplate restTemplate;
 * // private String userServiceUrl = "http://localhost:8080";
 * //
 * // public boolean isMale(Long userId) {
 * //     try {
 * //         var resp = restTemplate.getForEntity(
 * //             userServiceUrl + "/v1/users/" + userId + "/profile", Map.class);
 * //         // 解析 code=0, data.gender==1
 * //         ...
 * //     } catch (Exception e) { return false; }
 * // }
 * }
 * </pre>
 *
 * <h3>当前：阶段 3 — gRPC（正确方式）</h3>
 * 通过 @GrpcClient("user-service") 注入 gRPC stub，
 * 经 Nacos 服务发现连接到 user-service（端口 9090）。
 * gRPC stub 由 proto/user/user.proto 编译生成，包坐标 {@code user-proto-0.1.0}。
 */
@Slf4j
@Component
public class UserClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;

    /**
     * 取好友 user_id 列表（发帖写扩散用）。
     * TODO: 等 user-service 实现好友关系接口。
     */
    public List<Long> getFriendUserIds(Long userId) {
        log.debug("UserClient.getFriendUserIds called (stub, no friend list yet), userId={}", userId);
        return Collections.emptyList();
    }

    /**
     * 判断用户性别（gRPC → user-service GetProfile）。
     * 降级：user-service 不可用时返回 false（归入女性池，不影响 Feed 可用性）。
     */
    public boolean isMale(Long userId) {
        try {
            var req = GetProfileRequest.newBuilder().setUserId(userId).build();
            var resp = profileStub.getProfile(req);

            if (resp.getBase().getCode() == 0 && resp.hasProfile()) {
                boolean male = resp.getProfile().getGender() == 1;
                log.debug("UserClient.isMale: userId={} gender={} isMale={}", userId,
                        resp.getProfile().getGender(), male);
                return male;
            }
        } catch (StatusRuntimeException e) {
            log.warn("UserClient.isMale gRPC failed for userId={}, fallback to false", userId, e);
        } catch (Exception e) {
            log.warn("UserClient.isMale error for userId={}, fallback to false", userId, e);
        }
        return false;
    }

    /**
     * 批量获取性别映射（gRPC → user-service BatchGetProfile）。
     * 降级：全部用默认值（女性）。
     */
    public Map<Long, Boolean> getGenders(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        try {
            var req = BatchGetProfileRequest.newBuilder()
                    .addAllUserIds(userIds)
                    .build();
            var resp = profileStub.batchGetProfile(req);

            if (resp.getBase().getCode() == 0) {
                Map<Long, Boolean> result = new HashMap<>();
                for (var profile : resp.getProfilesList()) {
                    result.put(profile.getUserId(), profile.getGender() == 1);
                }
                log.debug("UserClient.getGenders: batch {} profiles via gRPC", result.size());
                if (!result.isEmpty()) return result;
            }
        } catch (Exception e) {
            log.warn("UserClient.getGenders failed, fallback to default", e);
        }

        // 降级：全部默认 false
        Map<Long, Boolean> fallback = new HashMap<>();
        for (Long uid : userIds) fallback.put(uid, false);
        log.debug("UserClient.getGenders: batch {} (fallback)", userIds.size());
        return fallback;
    }
}
