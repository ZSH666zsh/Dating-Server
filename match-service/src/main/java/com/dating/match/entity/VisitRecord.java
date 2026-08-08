package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 访问记录（"谁访问了我"）。
 * 对应 match-service-prd-tech.md §7.2 visit_record 表。
 */
@Data
@TableName("visit_record")
public class VisitRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;
    private Long toUserId;

    /** 1=BH 2=DH */
    private Integer fromUserType;

    /** 1=PROFILE_VIEW 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE */
    private Integer source;

    private Integer visitCount;  // UPSERT 累加（同一个人访问多次计数 +1）
    private OffsetDateTime visitedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
