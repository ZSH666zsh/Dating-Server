package com.dating.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 计数底座（已刷盘部分）
 */
@Data
@TableName("post_stats")
public class PostStat {
    @TableId(value = "post_id", type = IdType.INPUT)
    private Long postId;
    private Integer likeCount;  // 已刷盘的点赞数基准值
    private Integer commentCount;  // 已刷盘的评论数基准值

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
