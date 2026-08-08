package com.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户订阅。
 */
@Data
@TableName("user_subscription")
public class UserSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;  // UNIQUE（每人一条生效中订阅）

    private Integer tier;  // 1=FREE 2=WEEKLY 3=MONTHLY 4=YEARLY

    private OffsetDateTime expiresAt;  // 到期时间

    private String source;  // IAP_APPLE / IAP_GOOGLE / TEST / ADMIN

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
