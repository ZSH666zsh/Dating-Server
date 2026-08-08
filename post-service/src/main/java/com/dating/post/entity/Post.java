package com.dating.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 帖子主表
 * 帖子不存图片、不存计数。图片和计数都单独拆表。
 */
@Data
@TableName("posts")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;                // 内部物理主键，不对外

    private Long postId;            // 雪花 ID，业务主键
    private Long userId;            // 发帖人
    private String content;         // 文本
    private Integer status;         // 1=正常 0=删除 2=审核中

    @TableLogic
    private Integer deleted;        // 逻辑删除

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
