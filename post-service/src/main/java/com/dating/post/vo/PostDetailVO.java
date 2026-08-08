package com.dating.post.vo;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 帖子详情 VO
 */
@Data
@Builder
public class PostDetailVO {
    private Long postId;
    private Long userId;
    private String content;
    private List<String> imageKeys;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean isLiked;
}
