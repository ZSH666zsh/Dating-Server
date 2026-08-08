package com.dating.user.grpc;

import com.dating.user.constant.ErrorCode;
import com.dating.user.exception.BizException;
import com.dating.user.service.UserIdentityService;
import com.dating.zhaoshihang.proto.user.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC 身份服务实现 —— 4 个 RPC。
 *
 * resolveOrCreateByPhone → 调 UserIdentityService
 * resolveOrCreateByThirdParty
 * resolveOrCreateByDevice
 * checkBan
 *
 * @see UserIdentityService
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserIdentityGrpcService extends UserIdentityServiceGrpc.UserIdentityServiceImplBase {

    private final UserIdentityService userIdentityService;

    @Override
    public void resolveOrCreateByPhone(ResolveOrCreateByPhoneRequest req,
                                        StreamObserver<ResolveOrCreateByPhoneResponse> resp) {
        try {
            var result = userIdentityService.resolveOrCreateByPhone(req.getPhoneE164());
            resp.onNext(ResolveOrCreateByPhoneResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setUserId(result.userId())
                    .setPending(result.pending())
                    .setNewlyCreated(result.newlyCreated())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ResolveOrCreateByPhoneResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("resolveOrCreateByPhone error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void resolveOrCreateByThirdParty(ResolveOrCreateByThirdPartyRequest req,
                                              StreamObserver<ResolveOrCreateByThirdPartyResponse> resp) {
        try {
            var result = userIdentityService.resolveOrCreateByThirdParty(
                    req.getThirdPartyUserId(), req.getPlatform());
            resp.onNext(ResolveOrCreateByThirdPartyResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setUserId(result.userId())
                    .setPending(result.pending())
                    .setNewlyCreated(result.newlyCreated())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ResolveOrCreateByThirdPartyResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("resolveOrCreateByThirdParty error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void resolveOrCreateByDevice(ResolveOrCreateByDeviceRequest req,
                                         StreamObserver<ResolveOrCreateByDeviceResponse> resp) {
        try {
            var result = userIdentityService.resolveOrCreateByDevice(req.getDeviceId(), req.getPlatform());
            resp.onNext(ResolveOrCreateByDeviceResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setUserId(result.userId())
                    .setPending(result.pending())
                    .setNewlyCreated(result.newlyCreated())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ResolveOrCreateByDeviceResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("resolveOrCreateByDevice error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }

    @Override
    public void checkBan(CheckBanRequest req, StreamObserver<CheckBanResponse> resp) {
        try {
            boolean banned = userIdentityService.checkBan(req.getUserId());
            resp.onNext(CheckBanResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setBanned(banned)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("checkBan error", e);
            resp.onError(Status.INTERNAL.withDescription("服务器内部错误").asRuntimeException());
        }
    }
}
