package com.dating.user.grpc;

import com.dating.user.constant.ErrorCode;
import com.dating.user.exception.BizException;
import com.dating.user.service.UserProfileService;
import com.dating.user.vo.UserProfileVO;
import com.dating.zhaoshihang.proto.user.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * gRPC 档案服务实现 —— 7 个 RPC。
 *
 * getProfile / batchGetProfile / updateProfile / upsertOnboarding
 * replaceUserInterests / presignAvatarUpload / confirmAvatarUpload
 *
 * @see UserProfileService
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserProfileGrpcService extends UserProfileServiceGrpc.UserProfileServiceImplBase {

    private final UserProfileService userProfileService;

    @Override
    public void getProfile(GetProfileRequest req, StreamObserver<GetProfileResponse> resp) {
        try {
            var profile = userProfileService.getProfile(req.getUserId());
            resp.onNext(GetProfileResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setProfile(toProto(profile))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(GetProfileResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getProfile error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void batchGetProfile(BatchGetProfileRequest req, StreamObserver<BatchGetProfileResponse> resp) {
        try {
            var profiles = userProfileService.batchGetProfile(req.getUserIdsList());
            resp.onNext(BatchGetProfileResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .addAllProfiles(profiles.stream().map(this::toProto).toList())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("batchGetProfile error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest req, StreamObserver<UpdateProfileResponse> resp) {
        try {
            LocalDate birthday = null;
            if (req.hasBirthday()) birthday = LocalDate.parse(req.getBirthday());
            var profile = userProfileService.updateProfile(
                    req.getUserId(),
                    req.hasNickname() ? req.getNickname() : null,
                    req.hasGender() ? req.getGender() : null,
                    birthday,
                    req.hasCityId() ? req.getCityId() : null,
                    req.hasRace() ? req.getRace() : null,
                    req.hasBeautyScore() ? req.getBeautyScore() : null
            );
            resp.onNext(UpdateProfileResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setProfile(toProto(profile))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(UpdateProfileResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("updateProfile error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void upsertOnboarding(UpsertOnboardingRequest req, StreamObserver<UpsertOnboardingResponse> resp) {
        try {
            var profile = userProfileService.upsertOnboarding(
                    req.getUserId(), req.getNickname(), req.getGender(),
                    LocalDate.parse(req.getBirthday()), req.getCityId());
            resp.onNext(UpsertOnboardingResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setProfile(toProto(profile))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(UpsertOnboardingResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("upsertOnboarding error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void replaceUserInterests(ReplaceUserInterestsRequest req, StreamObserver<ReplaceUserInterestsResponse> resp) {
        try {
            var interests = req.getInterestsList().stream()
                    .map(i -> UserProfileVO.UserInterestVO.builder()
                            .tabKey(i.getTabKey())
                            .tagKey(i.getTagKey())
                            .picKey(i.getPicKey())
                            .build())
                    .toList();
            userProfileService.replaceUserInterests(req.getUserId(), interests);
            resp.onNext(ReplaceUserInterestsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("replaceUserInterests error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void presignAvatarUpload(PresignAvatarUploadRequest req, StreamObserver<PresignAvatarUploadResponse> resp) {
        try {
            String objectKey = userProfileService.presignAvatarUpload(req.getUserId(), req.getExt());
            // TODO: 替换为真实的 presigned URL（需接入 ObjectStorage）
            resp.onNext(PresignAvatarUploadResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setObjectKey(objectKey)
                    .setUploadUrl(objectKey) // 暂用 key 代替 URL
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(PresignAvatarUploadResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("presignAvatarUpload error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void confirmAvatarUpload(ConfirmAvatarUploadRequest req, StreamObserver<ConfirmAvatarUploadResponse> resp) {
        try {
            userProfileService.confirmAvatarUpload(req.getUserId(), req.getObjectKey());
            resp.onNext(ConfirmAvatarUploadResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ConfirmAvatarUploadResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("confirmAvatarUpload error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    // ─── 私有方法 ───

    private UserProfileProto toProto(UserProfileVO vo) {
        var builder = UserProfileProto.newBuilder()
                .setUserId(vo.getUserId())
                .setNickname(vo.getNickname() != null ? vo.getNickname() : "")
                .setAge(vo.getAge() != null ? vo.getAge() : 0)
                .setGender(vo.getGender() != null ? vo.getGender() : 0)
                .setBeautyScore(vo.getBeautyScore() != null ? vo.getBeautyScore() : 0)
                .setRace(vo.getRace() != null ? vo.getRace() : "")
                .setCustomAvatar(vo.getCustomAvatar() != null ? vo.getCustomAvatar() : "{}")
                .setUserType(vo.getUserType() != null ? vo.getUserType() : 1)
                .setPending(vo.getPending() != null ? vo.getPending() : true);

        if (vo.getCityId() != null) builder.setCityId(vo.getCityId());
        if (vo.getCityName() != null) builder.setCityName(vo.getCityName());
        if (vo.getStateCode() != null) builder.setStateCode(vo.getStateCode());
        if (vo.getCreatedAt() != null) builder.setCreatedAt(vo.getCreatedAt().toInstant().toEpochMilli());
        if (vo.getBirthday() != null) builder.setBirthday(vo.getBirthday().toString());

        if (vo.getInterests() != null) {
            builder.addAllInterests(vo.getInterests().stream()
                    .map(i -> InterestProto.newBuilder()
                            .setTabKey(i.getTabKey())
                            .setTagKey(i.getTagKey())
                            .setPicKey(i.getPicKey() != null ? i.getPicKey() : "")
                            .build())
                    .toList());
        }
        return builder.build();
    }
}
