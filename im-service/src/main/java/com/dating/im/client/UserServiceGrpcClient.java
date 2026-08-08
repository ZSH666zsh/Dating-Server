package com.dating.im.client;

import com.dating.zhaoshihang.proto.user.GetProfileRequest;
import com.dating.zhaoshihang.proto.user.UserProfileServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * user-service gRPC 客户端。用于查用户的 BH/DH 类型。
 */
@Slf4j
@Component
public class UserServiceGrpcClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;

    /** 获取用户类型。1=BH 2=DH。null 表示获取失败。 */
    public Integer getUserType(long userId) {
        try {
            var req = GetProfileRequest.newBuilder().setUserId(userId).build();
            var resp = profileStub.getProfile(req);
            if (resp.getBase().getCode() == 0 && resp.hasProfile()) {
                return resp.getProfile().getUserType();
            }
        } catch (StatusRuntimeException e) {
            log.warn("UserServiceGrpcClient.getUserType failed: userId={}", userId, e);
        }
        return null;
    }

    /** 获取用户昵称。 */
    public Optional<String> getNickname(long userId) {
        try {
            var req = GetProfileRequest.newBuilder().setUserId(userId).build();
            var resp = profileStub.getProfile(req);
            if (resp.getBase().getCode() == 0 && resp.hasProfile()) {
                return Optional.of(resp.getProfile().getNickname());
            }
        } catch (StatusRuntimeException e) {
            log.warn("UserServiceGrpcClient.getNickname failed: userId={}", userId, e);
        }
        return Optional.empty();
    }
}
