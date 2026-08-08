package com.dating.post.vo;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 帖子列表项 VO
 */
@Data
@Builder
public class PostItemVO {
    private Long postId;
    private String content;
    private OffsetDateTime createdAt;
}
