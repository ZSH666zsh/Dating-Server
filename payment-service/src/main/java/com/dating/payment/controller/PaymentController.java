package com.dating.payment.controller;

import com.dating.payment.constant.ErrorCode;
import com.dating.payment.exception.BizException;
import com.dating.payment.service.CoinService;
import com.dating.payment.service.PaymentOrderService;
import com.dating.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付/金币 REST 接口（本机调试用）。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final CoinService coinService;
    private final SubscriptionService subscriptionService;
    private final PaymentOrderService paymentOrderService;

    // ─── 金币 ───

    @GetMapping("/coins/balance")
    public Map<String, Object> getCoins(@RequestParam Long userId) {
        var account = coinService.getCoins(userId);
        return Map.of(
                "balance", account.getBalance(),
                "paidBalance", account.getPaidBalance(),
                "total", (account.getBalance() != null ? account.getBalance() : 0)
                        + (account.getPaidBalance() != null ? account.getPaidBalance() : 0)
        );
    }

    @PostMapping("/coins/add")
    public Map<String, Object> addCoins(@RequestParam Long userId, @RequestParam long amount,
                                         @RequestParam String reason) {
        var account = coinService.addCoins(userId, amount, reason);
        return Map.of("balance", account.getBalance());
    }

    @PostMapping("/coins/consume")
    public Map<String, Object> consumeCoins(@RequestParam Long userId, @RequestParam long amount,
                                              @RequestParam String reason) {
        try {
            var account = coinService.consumeCoins(userId, amount, reason, null, null);
            return Map.of("balance", account.getBalance() + account.getPaidBalance());
        } catch (BizException e) {
            return Map.of("code", e.getCode(), "message", e.getMessage());
        }
    }

    @GetMapping("/coins/ledger")
    public Map<String, Object> getLedger(@RequestParam Long userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        var result = coinService.getCoinLedger(userId, page, size);
        return Map.of("entries", result.entries(), "total", result.total());
    }

    // ─── 订阅 ───

    @GetMapping("/subscription")
    public Map<String, Object> getSubscription(@RequestParam Long userId) {
        var info = subscriptionService.getSubscription(userId);
        return Map.of("tier", info.tier(), "isActive", info.isActive());
    }

    @PostMapping("/subscription/activate")
    public Map<String, Object> activateSubscription(@RequestParam Long userId,
                                                     @RequestParam String tier,
                                                     @RequestParam int durationDays,
                                                     @RequestParam String source) {
        var info = subscriptionService.activateSubscription(userId, tier, durationDays, source);
        return Map.of("tier", info.tier(), "expiresAt", info.expiresAt());
    }

    // ─── 支付订单（测试模式） ───

    @PostMapping("/order/create")
    public Map<String, Object> createOrder(@RequestParam Long userId,
                                            @RequestParam String productId,
                                            @RequestParam(defaultValue = "TEST") String paymentMethod,
                                            @RequestParam(defaultValue = "USD") String currency,
                                            @RequestParam(defaultValue = "") String returnUrl,
                                            @RequestParam(defaultValue = "") String cancelUrl) {
        var result = paymentOrderService.createOrder(userId, productId, paymentMethod,
                currency, returnUrl, cancelUrl);
        return Map.of(
                "orderId", result.orderId(),
                "status", result.status(),
                "extOrderId", result.extOrderId(),
                "checkoutUrl", result.checkoutUrl()
        );
    }

    @PostMapping("/order/verify")
    public Map<String, Object> verifyPayment(@RequestParam String orderId,
                                              @RequestParam(defaultValue = "") String extOrderId,
                                              @RequestParam(defaultValue = "TEST") String paymentMethod) {
        var result = paymentOrderService.verifyPayment(orderId, extOrderId, paymentMethod);
        return Map.of("success", result.success(), "status", result.status());
    }
}
