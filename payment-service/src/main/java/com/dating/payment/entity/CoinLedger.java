package com.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 金币流水（append-only 审计）。
 */
@Data
@TableName("coin_ledger")
public class CoinLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;            // INCOME / EXPENSE
    private Long amount;  // 免费币变动
    private Long paidAmount;  // 付费币变动
    private Long balanceAfter;  // 变动后余额
    private Long paidBalanceAfter;
    private String reason;  // 变动原因（如 SUPER_HI、IM_MSG）
    private String extra;           // JSONB
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
