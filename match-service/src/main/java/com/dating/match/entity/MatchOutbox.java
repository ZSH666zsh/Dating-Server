package com.dating.match.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Match 副作用异步重试表。
 *
 * 匹配后通知 im-service 建会话、发消息是"副作用"，可能失败。
 * Match outbox 表负责持久化这些待执行任务，提供重试机制
 * （当前实现中通知是直接调用，outbox 表为后续重型化预留）
 *
 * 对应 match-service-prd-tech.md §7.2 match_outbox 表。
 */
@Data
@TableName("match_outbox")
public class MatchOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long matchId;

    /** ENSURE_CONVERSATION / SYSTEM_MSG / DH_OPENING */
    private String action;

    private String payloadJson;  // 执行所需参数
    private Integer attempts;
    private OffsetDateTime nextRetryAt;

    /** PENDING / DONE / DEAD */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
