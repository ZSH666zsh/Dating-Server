package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * DH 模拟互动任务（短生命周期，执行后硬删，不是软删除）
 * 对应 match-service-prd-tech.md §7.2 dh_interaction_task 表。
 */
@Data
@TableName("dh_interaction_task")
public class DhInteractionTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** DH user_id（发起方） */
    private Long fromUserId;

    /** 真人 user_id（接收方） */
    private Long toUserId;

    /** 1=LIKE 2=VISIT */
    private Integer action;

    /** 1=ONLINE 2=OFFLINE */
    private Integer scene;

    /** 计划执行时刻 */
    private OffsetDateTime executeTime;

    private String likeContent;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
