package com.dating.match.client;

import com.dating.zhaoshihang.proto.payment.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * payment-service gRPC 客户端。
 * 对应 match-service-prd-tech.md §7.7。
 *
 * <p>调 payment-service 的 CoinService（消费金币）+ PaymentService（查订阅）。
 */
@Slf4j
@Component
public class PaymentServiceClient {

    @GrpcClient("payment-service")
    private CoinServiceGrpc.CoinServiceBlockingStub coinStub;

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    /**
     * 获取用户订阅档位。
     */
    public String getSubscription(Long userId) {
        try {
            var req = GetSubscriptionRequest.newBuilder().setUserId(userId).build();
            var resp = paymentStub.getSubscription(req);
            if (resp.getBase().getCode() == 0) {
                log.debug("PaymentServiceClient.getSubscription: userId={} tier={}", userId, resp.getTier());
                return resp.getTier();
            }
        } catch (StatusRuntimeException e) {
            log.warn("PaymentServiceClient.getSubscription failed, fallback FREE for userId={}", userId);
        } catch (Exception e) {
            log.warn("PaymentServiceClient.getSubscription error, fallback FREE", e);
        }
        return "FREE";
    }

    /**
     * 检查是否 active 订阅。
     */
    public boolean hasActiveSubscription(Long userId) {
        String tier = getSubscription(userId);
        return !"FREE".equals(tier);
    }

    /**
     * 消费金币。
     *
     * @return true=扣币成功, false=余额不足
     */
    public boolean consumeCoins(Long userId, int amount, String reason, String idempotencyKey) {
        try {
            var req = ConsumeCoinsRequest.newBuilder()
                    .setUserId(userId)
                    .setAmount(amount)
                    .setReason(reason)
                    .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                    .build();
            var resp = coinStub.consumeCoins(req);
            boolean success = resp.getBase().getCode() == 0;
            if (success) {
                log.debug("PaymentServiceClient.consumeCoins: userId={} amount={} reason={} balanceAfter={}",
                        userId, amount, reason, resp.getBalanceAfter());
            } else {
                log.warn("PaymentServiceClient.consumeCoins failed: userId={} code={}",
                        userId, resp.getBase().getCode());
            }
            return success;
        } catch (StatusRuntimeException e) {
            log.warn("PaymentServiceClient.consumeCoins RPC failed: userId={}", userId, e);
            return false;
        }
    }
}
