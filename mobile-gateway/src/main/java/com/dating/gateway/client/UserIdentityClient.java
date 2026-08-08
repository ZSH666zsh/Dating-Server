package com.dating.gateway.client;

import com.dating.zhaoshihang.proto.user.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-service UserIdentityService gRPC 客户端。
 * 处理用户注册/登录（identity domain）和封禁检查。
 */
@Slf4j
@Component
public class UserIdentityClient {

    @GrpcClient("user-service")
    private UserIdentityServiceGrpc.UserIdentityServiceBlockingStub identityStub;

    /**
     * 手机号登录/注册。
     */
    public ResolveOrCreateByPhoneResponse resolveOrCreateByPhone(String phoneE164) {
        try {
            var req = ResolveOrCreateByPhoneRequest.newBuilder()
                    .setPhoneE164(phoneE164).build();
            return identityStub.resolveOrCreateByPhone(req);
        } catch (StatusRuntimeException e) {
            log.warn("resolveOrCreateByPhone RPC failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 第三方登录/注册。
     */
    public ResolveOrCreateByThirdPartyResponse resolveOrCreateByThirdParty(String thirdPartyUserId, int platform) {
        try {
            var req = ResolveOrCreateByThirdPartyRequest.newBuilder()
                    .setThirdPartyUserId(thirdPartyUserId)
                    .setPlatform(platform)
                    .build();
            return identityStub.resolveOrCreateByThirdParty(req);
        } catch (StatusRuntimeException e) {
            log.warn("resolveOrCreateByThirdParty RPC failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 设备匿名登录/注册。
     */
    public ResolveOrCreateByDeviceResponse resolveOrCreateByDevice(String deviceId, int platform) {
        try {
            var req = ResolveOrCreateByDeviceRequest.newBuilder()
                    .setDeviceId(deviceId)
                    .setPlatform(platform)
                    .build();
            return identityStub.resolveOrCreateByDevice(req);
        } catch (StatusRuntimeException e) {
            log.warn("resolveOrCreateByDevice RPC failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 封禁检查。
     */
    public CheckBanResponse checkBan(Long userId) {
        try {
            var req = CheckBanRequest.newBuilder().setUserId(userId).build();
            return identityStub.checkBan(req);
        } catch (StatusRuntimeException e) {
            log.warn("checkBan RPC failed: {}", e.getMessage());
            throw e;
        }
    }
}
