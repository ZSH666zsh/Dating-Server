package com.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.post.entity.PostComment;
import com.dating.post.mapper.PostCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评论管理，评论 CRUD
 */
@Component
@RequiredArgsConstructor
public class PostCommentManager {

    private final PostCommentMapper postCommentMapper;

    public void insert(PostComment comment) {
        postCommentMapper.insert(comment);
    }

    public PostComment getByCommentId(Long commentId) {
        return postCommentMapper.selectOne(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getCommentId, commentId)
                        .eq(PostComment::getDeleted, 0)
        );
    }

    /**
     * 一级评论分页（游标）
     */

    // DB 冷路
    public List<PostComment> listByPostId(Long postId, int pageSize, Long cursor) {
        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPostId, postId)
                .eq(PostComment::getRootId, 0)
                .eq(PostComment::getDeleted, 0)
                .orderByDesc(PostComment::getCommentId);

        // 用 comment_id < cursor 降序 + LIMIT 的游标分页
        if (cursor != null && cursor > 0) {
            wrapper.lt(PostComment::getCommentId, cursor);
        }
        return postCommentMapper.selectList(wrapper.last("LIMIT " + pageSize));
    }

    /**
     * 软删评论
     */
    public void deleteByCommentId(Long commentId) {
        postCommentMapper.delete(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getCommentId, commentId));
    }

    /**
     * 校验 commentId 是否属于该 user（删评论时鉴权用）
     */
    public boolean isOwner(Long commentId, Long userId) {
        PostComment c = getByCommentId(commentId);
        return c != null && c.getUserId().equals(userId);
    }
}
