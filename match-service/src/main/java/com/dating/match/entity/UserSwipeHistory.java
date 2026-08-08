package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 划卡历史。
 * 对应 match-service-prd-tech.md §7.2 user_swipe_history 表。
 */
@Data
@TableName("user_swipe_history")
public class UserSwipeHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long targetUserId;  // (user_id, target_user_id) UNIQUE 联合，防重复划卡

    private Integer targetUserType;  // 1=BH 2=D

    private Integer direction;  // 1=LEFT 2=RIGHT 3=SUPER_HI

    private OffsetDateTime swipedAt;  // 划卡时间

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
