package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/** 手机号登录绑定表 —— user_login_phone */
@Data
@TableName("user_login_phone")
public class UserLoginPhone {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String phoneE164;       // 带国家码，如 +8613800138000
    private String appName;         // 应用标识

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
