package com.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 支付订单。
 */
@Data
@TableName("payment_orders")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String orderId;         // 业务订单号（唯一）
    private String productId;

    private BigDecimal amount;
    private String currency;
    private String paymentChannel;  // 支付渠道：PAYPAL / APPLE_IAP / GOOGLE_BILLING

    private String status;  // 	INIT → PAID → GRANTED / FAILED

    private String refundStatus;    // NONE / PARTIAL / FULL
    private BigDecimal refundedAmount;
    private String extTransactionId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
