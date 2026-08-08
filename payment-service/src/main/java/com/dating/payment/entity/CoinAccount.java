package com.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 金币账户。
 * 每用户一行，乐观锁 version。
 */
@Data
@TableName("coin_accounts")
public class CoinAccount {

    @TableId
    private Long userId;

    private Long balance;           // 免费金币
    private Long paidBalance;       // 付费金币

    @Version
    private Integer version;  // 乐观锁

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
