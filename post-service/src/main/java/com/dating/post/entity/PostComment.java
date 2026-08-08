package com.dating.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 评论
 */
@Data
@TableName("post_comments")
public class PostComment {
    @TableId(type = IdType.AUTO)
    private Long id;                // 内部主键

    private Long commentId;         // 业务主键
    private Long postId;
    private Long userId;
    private String content;         // ≤ 512 字符
    private Long rootId;            // 根评论 ID，自身为根则 0
    private Long parentId;          // 直接父评论 ID
    private Long replyToUserId;     // 被回复人

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
