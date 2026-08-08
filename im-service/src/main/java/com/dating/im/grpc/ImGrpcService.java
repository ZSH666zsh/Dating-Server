package com.dating.im.grpc;

import com.dating.im.client.OpenImApiClient;
import com.dating.im.client.UserServiceGrpcClient;
import com.dating.im.security.LiveKitTokenGenerator;
import com.dating.im.service.*;
import com.dating.zhaoshihang.proto.im.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * ImService gRPC 实现。对应 im-service-design.md §1 / §9。
 *
 * <p>实现说明：
 * - onRawCallback：回调收口，解析 → 分发 → 扣费/反导流/AI → 返回决策码
 * - getImToken / registerImUser → OpenImApiClient（调 OpenIM REST API）
 * - generateCallToken → LiveKitTokenGenerator（HS256 JWT）
 * - sendBusinessNotification → OpenImApiClient（系统消息）
 *
 * <p>TODO（按优先级）：
 * <ol>
 *   <li>sendMessage → 调 OpenImSender 发消息（需要 OpenIM 可用）</li>
 * </ol>
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ImGrpcService extends ImServiceGrpc.ImServiceImplBase {

    private final CallbackService callbackService;
    private final ImEventDispatcher dispatcher;
    private final LiveKitTokenGenerator tokenGenerator;
    private final OpenImApiClient openImApiClient;

    @Override
    public void onRawCallback(RawCallbackRequest req, StreamObserver<RawCallbackResponse> resp) {
        try {
            var event = callbackService.parse(req.getProvider(), req.getPayload().toByteArray());
            int code = dispatcher.dispatch(event);
            resp.onNext(RawCallbackResponse.newBuilder()
                    .setSuccess(code == 0)
                    .setCode(code)
                    .setMessage(code == 0 ? "ok" : "rejected")
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("onRawCallback error", e);
            resp.onNext(RawCallbackResponse.newBuilder().setSuccess(true).setCode(0).build());
            resp.onCompleted();
        }
    }

    @Override
    public void sendMessage(SendMessageRequest req, StreamObserver<SendMessageResponse> resp) {
        log.debug("sendMessage: from={} to={} type={}", req.getFromUserId(), req.getToUserId(), req.getMessageType());
        // TODO: 调 OpenImSender 借助 OpenIM REST API 发送消息
        // 需要 OpenIM 可用 + 创建 OpenIM 消息体结构
        resp.onNext(SendMessageResponse.newBuilder().setSuccess(true)
                .setMessageId("im_" + System.currentTimeMillis()).build());
        resp.onCompleted();
    }

    @Override
    public void getImToken(GetImTokenRequest req, StreamObserver<GetImTokenResponse> resp) {
        try {
            String imToken = openImApiClient.getUserToken(req.getUserId());
            if (imToken == null) {
                // 降级：返回 stub token（本地开发用）
                log.warn("OpenIM getToken failed, using stub token for userId={}", req.getUserId());
                imToken = "im_token_stub_" + req.getUserId();
            }
            resp.onNext(GetImTokenResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(0))
                    .setImToken(imToken)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getImToken error", e);
            resp.onError(Status.INTERNAL.withDescription("获取IM Token失败").asRuntimeException());
        }
    }

    @Override
    public void registerImUser(RegisterImUserRequest req, StreamObserver<RegisterImUserResponse> resp) {
        try {
            boolean ok = openImApiClient.registerUser(req.getUserId(), req.getNickname(), req.getAvatarUrl());
            resp.onNext(RegisterImUserResponse.newBuilder()
                    .setSuccess(ok)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("registerImUser error", e);
            resp.onError(Status.INTERNAL.withDescription("注册IM用户失败").asRuntimeException());
        }
    }

    @Override
    public void generateCallToken(GenerateCallTokenRequest req, StreamObserver<GenerateCallTokenResponse> resp) {
        try {
            String token = tokenGenerator.generateToken(req.getUserId(), req.getPeerId());
            resp.onNext(GenerateCallTokenResponse.newBuilder()
                    .setToken(token).setRoom("call_" + System.currentTimeMillis() % 100000000)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("generateCallToken error", e);
            resp.onError(Status.INTERNAL.asRuntimeException());
        }
    }

    @Override
    public void sendBusinessNotification(SendBusinessNotificationRequest req,
                                          StreamObserver<SendBusinessNotificationResponse> resp) {
        try {
            openImApiClient.sendBusinessNotification(
                    req.getSendUserId(), req.getRecvUserId(), req.getKey(), req.getData());
            resp.onNext(SendBusinessNotificationResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(0)).build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("sendBusinessNotification error", e);
            resp.onError(Status.INTERNAL.withDescription("发送通知失败").asRuntimeException());
        }
    }
}
