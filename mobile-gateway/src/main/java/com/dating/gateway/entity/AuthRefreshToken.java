package com.dating.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Refresh token（非 JWT，opaque string SHA-256 入库）。
 */
@Data
@TableName("auth_refresh_token")
public class AuthRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String deviceId;
    private String tokenHash;       // SHA-256(64字符)
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;  // 轮换后标记已用
    private Long rotatedToId;       // 下一代的 id（链表）

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
