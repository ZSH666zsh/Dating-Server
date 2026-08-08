package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/** 设备绑定表 —— user_device_registration */
@Data
@TableName("user_device_registration")
public class UserDeviceRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String deviceId;
    private Integer platform;           // 1=iOS 2=Android 3=Web

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
