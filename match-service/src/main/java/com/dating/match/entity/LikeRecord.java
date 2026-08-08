package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 喜欢记录（"谁 Like 了我"）。
 * 对应 match-service-prd-tech.md §7.2 like_record 表。
 */
@Data
@TableName("like_record")
public class LikeRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;
    private Long toUserId;

    /** 1=BH 2=DH */
    private Integer fromUserType;

    /** 1=SWIPE_RIGHT 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE */
    private Integer source;

    private String likeContent;
    private OffsetDateTime likedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
