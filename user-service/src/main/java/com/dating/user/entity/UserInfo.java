package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用户主档案表 —— user_info
 * 对应 1ARCHITECTURE.md §6.2
 */
@Data
@TableName("user_info")
public class UserInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;            // 业务主键（雪花 ID）
    private String nickname;        // 昵称
    private Integer age;            // 年龄
    private Integer gender;         // 0=未设置 1=男 2=女
    private LocalDate birthday;     // 生日
    private Long cityId;            // 城市 ID
    private java.math.BigDecimal lat;   // 纬度
    private java.math.BigDecimal lng;   // 经度
    private Integer beautyScore;    // 颜值分 0-100
    private String race;            // 人种
    private String customAvatar;    // JSONB: {originalKey, minKey, midKey}
    private Integer regulationStatus; // 0=正常 1=审核 2=封禁 3=暂停
    private Boolean pending;        // true=待 onboarding
    private Integer userType;       // 1=BH 2=DH
    private OffsetDateTime lastOpenAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
