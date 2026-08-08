package com.dating.gateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 设备指纹登记。
 */
@Data
@TableName("auth_device")
public class AuthDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String deviceId;
    private Integer platform;     // 1=iOS 2=Android 3=Web
    private String pushToken;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
