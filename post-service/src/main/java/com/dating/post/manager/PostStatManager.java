package com.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dating.post.entity.PostStat;
import com.dating.post.mapper.PostStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 计数底座管理（刷盘专用）
 */
@Component
@RequiredArgsConstructor
public class PostStatManager {

    private final PostStatMapper postStatMapper;

    /**
     * 增量更新计数（点赞/评论刷盘时用）,直接在 SQL 层面做增量更新，不是先查再设。
     */

    // SET like_count = like_count + delta
    public void incrementLikeCount(Long postId, int delta) {
        postStatMapper.update(null, new LambdaUpdateWrapper<PostStat>()
                .eq(PostStat::getPostId, postId)
                .setSql("like_count = like_count + " + delta));
    }

    // SET comment_count = comment_count + delta
    public void incrementCommentCount(Long postId, int delta) {
        postStatMapper.update(null, new LambdaUpdateWrapper<PostStat>()
                .eq(PostStat::getPostId, postId)
                .setSql("comment_count = comment_count + " + delta));
    }
}
