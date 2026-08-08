package com.dating.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 点赞幂等记录
 */
@Data
@TableName("post_likes")
public class PostLike {
    private Long userId;
    private Long postId;
    private Integer status;         // 1=已赞 0=已取消

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
