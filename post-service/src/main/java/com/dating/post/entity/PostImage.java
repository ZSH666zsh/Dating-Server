package com.dating.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 帖子图片
 *
 * 一帖最多 9 张图。
 */
@Data
@TableName("post_images")
public class PostImage {
    private Long postId;
    private Integer sortOrder;      // 联合主键，sort_order 0..8
    private String imageKey;        // 对象存储 key，不存 URL

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
