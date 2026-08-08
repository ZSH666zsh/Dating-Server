package com.dating.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.payment.constant.ErrorCode;
import com.dating.payment.entity.PaymentOrder;
import com.dating.payment.exception.BizException;
import com.dating.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 支付订单服务。
 * 对应 payment-service-design.md §8 支付流程。
 *
 * <p>当前为测试模式（Test Mode）实现，走完整 DB 订单状态流转 + 金币发放，
 * 但跳过真实 PayPal API 调用。
 *
 * <h3>后续接入真实 PayPal</h3>
 * <p>TODO: 当对接真实 PayPal 时，需要：
 * <ol>
 *   <li>在 {@link #createOrder} 中调 PayPal CreateOrder API → 获取 approval_url</li>
 *   <li>在 {@link #verifyPayment} 中调 PayPal CaptureOrder API → 确认支付成功</li>
 *   <li>加 PayPal Webhook 回调端点（异步通知）</li>
 *   <li>引入 PayPal SDK 依赖：{@code com.paypal.sdk:checkout-sdk}</li>
 * </ol>
 * 以下实现中已在对应位置标注 {@code // PAYPAL_TODO}，方便搜索替换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final CoinService coinService;

    /** 商品定价表（测试模式直接硬编码） */
    private static final java.util.Map<String, ProductConfig> PRODUCTS = java.util.Map.of(
            "coin_100",      new ProductConfig("金币 100",      new BigDecimal("1.99"),  "USD", 100L,  ""),
            "coin_500",      new ProductConfig("金币 500",      new BigDecimal("4.99"),  "USD", 500L,  ""),
            "coin_1200",     new ProductConfig("金币 1200",     new BigDecimal("9.99"),  "USD", 1200L, ""),
            "sub_weekly",    new ProductConfig("周订阅",        new BigDecimal("6.99"),  "USD", 0L,    "WEEKLY"),
            "sub_monthly",   new ProductConfig("月订阅",        new BigDecimal("19.99"), "USD", 0L,    "MONTHLY"),
            "sub_yearly",    new ProductConfig("年订阅",        new BigDecimal("99.99"), "USD", 0L,    "YEARLY")
    );

    // ──────────────────────────────────────────────
    //  创建订单
    // ──────────────────────────────────────────────

    /**
     * 创建支付订单。
     *
     * <p>测试模式：直接 INSERT 订单记录（status=INIT），返回内部 orderId。
     * 不调外部支付网关。
     *
     * <p>PAYPAL_TODO: 接入真实 PayPal 后，在此处调 PayPal CreateOrder API：
     * <pre>{@code
     *     OrdersCreateRequest request = new OrdersCreateRequest();
     *     request.requestBody(new OrderRequest()
     *         .intent("CAPTURE")
     *         .purchaseUnits(List.of(...)));
     *     Order paypalOrder = client.execute(request);
     *     extOrderId = paypalOrder.id();
     *     checkoutUrl = paypalOrder.links().stream()
     *         .filter(l -> "payer-action".equals(l.rel()))
     *         .findFirst().map(LinkDescription::href).orElse("");
     * }</pre>
     *
     * @param userId        当前用户
     * @param productId     商品 ID（如 "coin_100" / "sub_monthly"）
     * @param paymentMethod 支付方式（PAYPAL / APPLE_IAP / GOOGLE_BILLING）
     * @param currency      币种（USD）
     * @param returnUrl     成功跳转 URL（PayPal 回调用，当前未使用）
     * @param cancelUrl     取消跳转 URL（当前未使用）
     * @return 订单创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResult createOrder(Long userId, String productId, String paymentMethod,
                                          String currency, String returnUrl, String cancelUrl) {
        // 1. 查商品配置
        ProductConfig config = PRODUCTS.get(productId);
        if (config == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "商品不存在: " + productId);
        }

        // 2. 生成内部订单号
        String orderId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // 3. 插入订单表
        PaymentOrder order = new PaymentOrder();
        order.setUserId(userId);
        order.setOrderId(orderId);
        order.setProductId(productId);
        order.setAmount(config.price());
        order.setCurrency(currency != null ? currency : config.currency());
        order.setPaymentChannel(paymentMethod != null ? paymentMethod : "TEST");
        order.setStatus("INIT");
        order.setRefundStatus("NONE");
        order.setRefundedAmount(BigDecimal.ZERO);
        paymentOrderMapper.insert(order);

        // PAYPAL_TODO: 调 PayPal CreateOrder API，获取 extOrderId 和 checkoutUrl
        // 当前测试模式：extOrderId 置空，checkoutUrl 置空（模拟直接支付成功）
        String extOrderId = "";
        String checkoutUrl = "";

        log.info("Order created: orderId={} userId={} product={} amount={} method={}",
                orderId, userId, productId, config.price(), paymentMethod);

        return new OrderCreateResult(orderId, "INIT", extOrderId, checkoutUrl);
    }

    // ──────────────────────────────────────────────
    //  确认支付
    // ──────────────────────────────────────────────

    /**
     * 确认/验证支付。
     *
     * <p>测试模式：直接将订单从 INIT → PAID → GRANTED，
     * 并根据商品类型发放金币或激活订阅。
     *
     * <p>PAYPAL_TODO: 接入真实 PayPal 后，在此处调 PayPal CaptureOrder API：
     * <pre>{@code
     *     OrdersCaptureRequest request = new OrdersCaptureRequest(extOrderId);
     *     Order paypalOrder = client.execute(request);
     *     if (!"COMPLETED".equals(paypalOrder.status())) {
     *         throw new BizException(PAYMENT_FAILED, "支付未完成");
     *     }
     * }</pre>
     *
     * @param orderId      内部订单号
     * @param extOrderId   第三方交易号（PayPal order id，测试模式可为空）
     * @param paymentMethod 支付方式
     * @return 支付验证结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVerifyResult verifyPayment(String orderId, String extOrderId, String paymentMethod) {
        // 1. 查订单
        PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, orderId));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderId);
        }

        // 2. 幂等：已 GRANTED → 直接返回成功
        if ("GRANTED".equals(order.getStatus())) {
            log.debug("Order already granted: orderId={}", orderId);
            return new OrderVerifyResult(true, "GRANTED");
        }

        // 3. 校验状态只能从 INIT 流转
        if (!"INIT".equals(order.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "订单状态异常: " + order.getStatus());
        }

        // PAYPAL_TODO: 调 PayPal CaptureOrder API 校验支付
        // 测试模式：直接标记为成功
        order.setStatus("PAID");
        order.setExtTransactionId(extOrderId != null ? extOrderId : "TEST_TX_" + orderId);
        order.setUpdatedAt(OffsetDateTime.now());
        paymentOrderMapper.updateById(order);

        // 4. 根据商品类型发放权益
        ProductConfig config = PRODUCTS.get(order.getProductId());
        if (config == null) {
            // 兜底：如果商品配置找不到，订单已经是 PAID，不阻塞
            log.warn("Product config not found for productId={}, orderId={}", order.getProductId(), orderId);
        } else {
            if (config.coinAmount() > 0) {
                // 金币商品 → 发放付费金币
                coinService.addPaidCoins(
                        order.getUserId(),
                        config.coinAmount(),
                        "PAYMENT:" + order.getProductId()
                );
                log.info("Coins granted: userId={} amount={} orderId={}",
                        order.getUserId(), config.coinAmount(), orderId);
            }
            // 订阅商品 → 订单只负责支付记录，订阅激活由 ActivateSubscription RPC 完成
            // （支付成功后客户端/服务端调 ActivateSubscription 激活订阅）
        }

        // 5. 标记 GRANTED（权益已发放）
        order.setStatus("GRANTED");
        paymentOrderMapper.updateById(order);

        log.info("Payment verified: orderId={} extTxId={} product={} status=GRANTED",
                orderId, order.getExtTransactionId(), order.getProductId());

        return new OrderVerifyResult(true, "GRANTED");
    }

    // ─── 内部类型 ───

    /** 商品配置 */
    private record ProductConfig(String name, BigDecimal price, String currency,
                                  long coinAmount, String subscriptionTier) {
        ProductConfig(String name, BigDecimal price, String currency, long coinAmount, String subscriptionTier) {
            this.name = name;
            this.price = price;
            this.currency = currency;
            this.coinAmount = coinAmount;
            this.subscriptionTier = subscriptionTier;
        }
        /** 是订阅商品 */
        boolean isSubscription() { return subscriptionTier != null && !subscriptionTier.isEmpty(); }
    }

    /** 创建订单结果 */
    public record OrderCreateResult(String orderId, String status,
                                     String extOrderId, String checkoutUrl) {}

    /** 支付验证结果 */
    public record OrderVerifyResult(boolean success, String status) {}
}
