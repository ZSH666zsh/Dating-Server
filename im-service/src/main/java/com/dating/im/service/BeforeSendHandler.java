package com.dating.im.service;

import com.dating.im.client.PaymentGrpcClient;
import com.dating.im.client.UserServiceGrpcClient;
import com.dating.im.config.ImConfig;
import com.dating.im.model.event.MessageBeforeSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Before-send 处理器 —— 发消息前的安检（可拦截）。
 * 对应 im-service-design.md §4.2。
 *
 * <p>决策顺序（短路）：
 * 1. 解析 senderId 失败 → allow
 * 2. sender 是 DH → allow
 * 3. 反导流命中 TEXT → REJECT 5002
 * 4. 扣费开关开 → 预检余额，不足拒发 5003，够则异步扣
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeforeSendHandler {

    private final UserServiceGrpcClient userClient;
    private final PaymentGrpcClient paymentClient;
    private final ImConfig config;
    private final CoinChargeDispatcher chargeDispatcher;
    private final ContactInfoDetector contactDetector;

    /** 处理 before-send 事件。返回 0=放行，非0=拒发。 */
    public int handle(MessageBeforeSendEvent event) {
        // 1. senderId 无法解析 → 放行（不误伤）
        if (event.senderId() <= 0) {
            log.warn("BeforeSend: cannot parse senderId, allow");
            return 0;
        }

        // 2. sender 是 DH → 放行（AI 回复不安检、不扣费）
        Integer senderType = userClient.getUserType(event.senderId());
        if (senderType != null && senderType == 2) {
            return 0;
        }

        // 3. 反导流
        if (config.isAntiFunnelEnabled() && "TEXT".equals(event.contentType())) {
            String hit = contactDetector.detect(event.content());
            if (hit != null) {
                log.warn("BeforeSend: anti-funnel hit, rule={} senderId={}",
                        hit, event.senderId());
                return 5002; // REJECT_CONTACT_INFO
            }
        }

        // 4. 扣费
        if (config.isChargeEnabled()) {
            try {
                long balance = paymentClient.getCoins(event.senderId());
                if (balance < config.getCoinCost()) {
                    log.warn("BeforeSend: insufficient coins, userId={} balance={}",
                            event.senderId(), balance);
                    return 5003; // REJECT_INSUFFICIENT_COINS
                }
                // 异步扣费
                chargeDispatcher.chargeAsync(event.senderId(), config.getCoinCost(),
                        "im-msg:" + event.messageId());
            } catch (Exception e) {
                log.error("BeforeSend: payment unavailable, reject", e);
                return 5004; // REJECT_PAYMENT_UNAVAILABLE
            }
        }

        return 0; // allow
    }
}
