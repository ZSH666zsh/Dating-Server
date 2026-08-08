package com.dating.im.service;

import com.dating.im.client.PaymentGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步扣费调度器。对应 im-service-design.md §6。
 * before-send 放行后，异步调 payment-service 扣费。
 * 幂等键 im-msg:<messageId> 防止重复扣。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinChargeDispatcher {

    private final PaymentGrpcClient paymentClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /** 异步扣费（best-effort，不阻塞主流程）。 */
    public void chargeAsync(long userId, int amount, String idempotencyKey) {
        executor.submit(() -> {
            try {
                boolean ok = paymentClient.consumeCoins(userId, amount, "im_message_send", idempotencyKey);
                if (ok) {
                    log.debug("CoinCharge success: userId={} amount={} key={}", userId, amount, idempotencyKey);
                } else {
                    log.warn("CoinCharge insufficient: userId={} amount={} key={}", userId, amount, idempotencyKey);
                }
            } catch (Exception e) {
                log.error("CoinCharge failed: userId={} key={}", userId, idempotencyKey, e);
            }
        });
    }
}
