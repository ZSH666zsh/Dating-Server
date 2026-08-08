package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/** 第三方账号绑定表 —— user_third_party_registration */
@Data
@TableName("user_third_party_registration")
public class UserThirdPartyRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String thirdPartyLoginUserId;   // 第三方平台用户 ID
    private Integer platform;               // 1=Google 2=Facebook 3=Apple

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
