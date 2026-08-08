package com.dating.payment.grpc;

import com.dating.payment.constant.ErrorCode;
import com.dating.payment.exception.BizException;
import com.dating.payment.service.PaymentOrderService;
import com.dating.payment.service.SubscriptionService;
import com.dating.zhaoshihang.proto.payment.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * 支付/订阅 gRPC 服务端实现。
 *
 * <h3>当前状态</h3>
 * <ul>
 *   <li>{@link #getSubscription} / {@link #activateSubscription} ✅ 真实实现</li>
 *   <li>{@link #createOrder} / {@link #verifyPayment} ✅ 测试模式实现（跳过真实 PayPal）</li>
 * </ul>
 *
 * <h3>后续接入真实 PayPal</h3>
 * <p>搜索 {@code PAYPAL_TODO} 定位需要替换的代码位置。
 * 需要引入 PayPal SDK、添加 sandbox 配置、替换测试模式为真实 API 调用。
 * @see PaymentOrderService
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final SubscriptionService subscriptionService;
    private final PaymentOrderService paymentOrderService;

    @Override
    public void getSubscription(GetSubscriptionRequest req, StreamObserver<GetSubscriptionResponse> resp) {
        try {
            var info = subscriptionService.getSubscription(req.getUserId());
            resp.onNext(GetSubscriptionResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setTier(info.tier())
                    .setExpiresAt(info.expiresAt() != null
                            ? info.expiresAt().toInstant().toEpochMilli() : 0)
                    .setIsActive(info.isActive())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getSubscription error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    @Override
    public void activateSubscription(ActivateSubscriptionRequest req, StreamObserver<ActivateSubscriptionResponse> resp) {
        try {
            var info = subscriptionService.activateSubscription(
                    req.getUserId(), req.getTier(), req.getDurationDays(), req.getSource());
            resp.onNext(ActivateSubscriptionResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setTier(info.tier())
                    .setExpiresAt(info.expiresAt() != null
                            ? info.expiresAt().toInstant().toEpochMilli() : 0)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("activateSubscription error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    /**
     * 创建支付订单。
     *
     * <p>当前为测试模式，创建订单记录后直接返回，不调 PayPal API。
     * 后续替换为真实 PayPal CreateOrder（搜索 PAYPAL_TODO）。
     */
    @Override
    public void createOrder(CreateOrderRequest req, StreamObserver<CreateOrderResponse> resp) {
        try {
            var result = paymentOrderService.createOrder(
                    GrpcServerInterceptor.getCurrentUserId(),
                    req.getProductId(),
                    req.getPaymentMethod(),
                    req.getCurrency(),
                    req.getReturnUrl(),
                    req.getCancelUrl()
            );

            resp.onNext(CreateOrderResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setOrderId(result.orderId())
                    .setStatus(result.status())
                    .setExtOrderId(result.extOrderId())
                    .setCheckoutUrl(result.checkoutUrl())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(CreateOrderResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("createOrder error", e);
            resp.onError(Status.INTERNAL.withDescription("创建订单失败").asRuntimeException());
        }
    }

    /**
     * 确认支付。
     *
     * <p>当前为测试模式，直接将 INIT → PAID → GRANTED + 发放金币。
     * 后续替换为真实 PayPal CaptureOrder（搜索 PAYPAL_TODO）。
     */
    @Override
    public void verifyPayment(VerifyPaymentRequest req, StreamObserver<VerifyPaymentResponse> resp) {
        try {
            var result = paymentOrderService.verifyPayment(
                    req.getOrderId(),
                    req.getExtOrderId(),
                    req.getPaymentMethod()
            );

            resp.onNext(VerifyPaymentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK).setMessage("ok"))
                    .setSuccess(result.success())
                    .setStatus(result.status())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(VerifyPaymentResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("verifyPayment error", e);
            resp.onError(Status.INTERNAL.withDescription("支付验证失败").asRuntimeException());
        }
    }
}
