package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 匹配关系。
 * 对应 match-service-prd-tech.md §7.2 match 表。
 */
@Data
@TableName("match")
public class MatchEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userIdLow;

    /** (user_id_low, user_id_high)
     * UNIQUE，low = min(A,B)，high = max(A,B)
     * 保证同一对 (A,B) 只有一条记录，不管谁先发起匹配。
     * */
    private Long userIdHigh;

    private OffsetDateTime matchedAt;

    private String source;  // SWIPE_MATCH / SWIPE_SUPER_HI

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
