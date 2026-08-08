package com.dating.gateway.client;

import com.dating.zhaoshihang.proto.user.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-service UserProfileService gRPC 客户端。
 * 处理用户资料读写、onboarding、头像上传。
 */
@Slf4j
@Component
public class UserProfileClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub profileStub;

    /**
     * 获取用户资料。
     */
    public GetProfileResponse getProfile(Long userId) {
        try {
            var req = GetProfileRequest.newBuilder().setUserId(userId).build();
            return profileStub.getProfile(req);
        } catch (StatusRuntimeException e) {
            log.warn("getProfile RPC failed: userId={}", userId, e);
            throw e;
        }
    }

    /**
     * 批量获取用户资料。
     */
    public BatchGetProfileResponse batchGetProfile(java.util.List<Long> userIds) {
        try {
            var req = BatchGetProfileRequest.newBuilder().addAllUserIds(userIds).build();
            return profileStub.batchGetProfile(req);
        } catch (StatusRuntimeException e) {
            log.warn("batchGetProfile RPC failed", e);
            throw e;
        }
    }

    /**
     * 更新资料。
     */
    public UpdateProfileResponse updateProfile(Long userId, String nickname, Integer gender,
                                                String birthday, Long cityId, String race, Integer beautyScore) {
        try {
            var builder = UpdateProfileRequest.newBuilder().setUserId(userId);
            if (nickname != null) builder.setNickname(nickname);
            if (gender != null) builder.setGender(gender);
            if (birthday != null) builder.setBirthday(birthday);
            if (cityId != null) builder.setCityId(cityId);
            if (race != null) builder.setRace(race);
            if (beautyScore != null) builder.setBeautyScore(beautyScore);
            return profileStub.updateProfile(builder.build());
        } catch (StatusRuntimeException e) {
            log.warn("updateProfile RPC failed", e);
            throw e;
        }
    }

    /**
     * Onboarding 首次完善资料。
     */
    public UpsertOnboardingResponse upsertOnboarding(Long userId, String nickname, int gender,
                                                      String birthday, Long cityId) {
        try {
            var req = UpsertOnboardingRequest.newBuilder()
                    .setUserId(userId).setNickname(nickname)
                    .setGender(gender).setBirthday(birthday).setCityId(cityId)
                    .build();
            return profileStub.upsertOnboarding(req);
        } catch (StatusRuntimeException e) {
            log.warn("upsertOnboarding RPC failed", e);
            throw e;
        }
    }

    /**
     * 替换兴趣标签。
     */
    public void replaceUserInterests(Long userId, java.util.List<InterestProto> interests) {
        try {
            var req = ReplaceUserInterestsRequest.newBuilder()
                    .setUserId(userId)
                    .addAllInterests(interests)
                    .build();
            profileStub.replaceUserInterests(req);
        } catch (StatusRuntimeException e) {
            log.warn("replaceUserInterests RPC failed", e);
            throw e;
        }
    }

    /**
     * 获取头像上传 presigned URL。
     */
    public PresignAvatarUploadResponse presignAvatarUpload(Long userId, String ext) {
        try {
            var req = PresignAvatarUploadRequest.newBuilder()
                    .setUserId(userId).setExt(ext).build();
            return profileStub.presignAvatarUpload(req);
        } catch (StatusRuntimeException e) {
            log.warn("presignAvatarUpload RPC failed", e);
            throw e;
        }
    }

    /**
     * 确认头像上传完成。
     */
    public void confirmAvatarUpload(Long userId, String objectKey) {
        try {
            var req = ConfirmAvatarUploadRequest.newBuilder()
                    .setUserId(userId).setObjectKey(objectKey).build();
            profileStub.confirmAvatarUpload(req);
        } catch (StatusRuntimeException e) {
            log.warn("confirmAvatarUpload RPC failed", e);
            throw e;
        }
    }
}
