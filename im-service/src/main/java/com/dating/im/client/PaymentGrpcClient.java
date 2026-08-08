package com.dating.im.client;

import com.dating.zhaoshihang.proto.payment.CoinServiceGrpc;
import com.dating.zhaoshihang.proto.payment.ConsumeCoinsRequest;
import com.dating.zhaoshihang.proto.payment.GetCoinsRequest;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * payment-service gRPC 客户端。对应 im-service-design.md §6。
 * 用于聊天扣费：余额预检（800ms deadline）和异步扣费。
 */
@Slf4j
@Component
public class PaymentGrpcClient {

    @GrpcClient("payment-service")
    private CoinServiceGrpc.CoinServiceBlockingStub coinStub;

    /** 查询金币余额（只读，短超时 800ms）。 */
    public long getCoins(long userId) {
        try {
            var req = GetCoinsRequest.newBuilder().setUserId(userId).build();
            var resp = coinStub.getCoins(req);
            return resp.getTotalBalance();
        } catch (StatusRuntimeException e) {
            log.warn("PaymentGrpcClient.getCoins failed: userId={}", userId, e);
            return 0;
        }
    }

    /** 消费金币（写操作，2s 超时）。返回 true=成功 false=余额不足。 */
    public boolean consumeCoins(long userId, int amount, String reason, String idempotencyKey) {
        try {
            var req = ConsumeCoinsRequest.newBuilder()
                    .setUserId(userId).setAmount(amount)
                    .setReason(reason).setIdempotencyKey(idempotencyKey)
                    .build();
            var resp = coinStub.consumeCoins(req);
            return resp.getBase().getCode() == 0;
        } catch (StatusRuntimeException e) {
            log.warn("PaymentGrpcClient.consumeCoins failed: userId={}", userId, e);
            return false;
        }
    }
}
