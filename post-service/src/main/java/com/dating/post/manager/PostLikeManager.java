package com.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.post.entity.PostLike;
import com.dating.post.mapper.PostLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 点赞管理。
 *
 * upsert 走 XML 里的 PostgreSQL ON CONFLICT DO UPDATE（原子操作）：
 * - 新记录 → INSERT
 * - 已存在且状态不同 → UPDATE
 * - 已存在且状态相同 → WHERE 条件阻止更新（返回 0 影响行）
 *
 * 比 Java if-else 先 select 再判断的写法更原子、防并发重复。
 */
@Component
@RequiredArgsConstructor
public class PostLikeManager {

    private final PostLikeMapper postLikeMapper;

    /**
     * 点赞 upsert（原子幂等）
     *
     * @return true 表示状态真变了（新增或切换），false 表示已是指定状态
     */
    public boolean upsert(Long userId, Long postId, int status) {
        // upsert() 返回 SQL 影响行数：
        //   > 0 → 插入或更新成功（状态真变了）
        //   = 0 → WHERE 条件阻止了更新（状态已是指定值，幂等无变化）
        // 这是原子操作，不会有并发问题。

        // true = 状态真变了（新增或切换）
        // false = 幂等跳过（已经是指定状态）
        return postLikeMapper.upsert(userId, postId, status) > 0;
    }

    /**
     * 查当前用户是否已点赞
     */
    public boolean isLiked(Long userId, Long postId) {
        PostLike like = postLikeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getStatus, 1)
        );
        return like != null;
    }
}
