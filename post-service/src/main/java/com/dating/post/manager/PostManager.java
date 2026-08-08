package com.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.post.entity.Post;
import com.dating.post.entity.PostImage;
import com.dating.post.entity.PostStat;
import com.dating.post.mapper.PostImageMapper;
import com.dating.post.mapper.PostMapper;
import com.dating.post.mapper.PostStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 帖子聚合管理：单表多次调用 + 缓存读写
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostManager {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostStatMapper postStatMapper;

    // ─── 写 ───

    /**
     * 创建帖子（事务内：posts + images + stats 三表）
     *
     * 虽然红线禁止多表 JOIN，但不禁止在一个 Manager 里依次调用多个单表操作。createPost 方法在 @Transactional 事务内依次 INSERT 三张表
     * 但 SQL 上仍然是 3 条独立的 INSERT，没有 JOIN。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createPost(Post post, List<String> imageKeys) {
        // 1. 插主表
        postMapper.insert(post);

        // 2. 插图片
        for (int i = 0; i < imageKeys.size(); i++) {
            PostImage img = new PostImage();
            img.setPostId(post.getPostId());
            img.setSortOrder(i);
            img.setImageKey(imageKeys.get(i));
            postImageMapper.insert(img);
        }

        // 3. 插计数底座
        PostStat stat = new PostStat();
        stat.setPostId(post.getPostId());
        stat.setLikeCount(0);
        stat.setCommentCount(0);
        postStatMapper.insert(stat);
    }

    // ─── 读 ───

    public Post getByPostId(Long postId) {
        return postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getPostId, postId)
                        .eq(Post::getDeleted, 0)
        );
    }

    public List<PostImage> getImagesByPostId(Long postId) {
        return postImageMapper.selectList(
                new LambdaQueryWrapper<PostImage>()
                        .eq(PostImage::getPostId, postId)
                        .orderByAsc(PostImage::getSortOrder)
        );
    }

    public PostStat getStatByPostId(Long postId) {
        return postStatMapper.selectById(postId);
    }

    public List<Post> listUserPosts(Long userId, int pageSize, Long cursor) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getDeleted, 0)
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getPostId);
        if (cursor != null && cursor > 0) {
            wrapper.lt(Post::getPostId, cursor);
        }
        return postMapper.selectList(wrapper.last("LIMIT " + pageSize));
    }

    // ─── 删 ───

    /**
     * 软删帖子
     */
    public void deleteByPostId(Long postId) {
        postMapper.delete(new LambdaQueryWrapper<Post>()
                .eq(Post::getPostId, postId));
    }

    /**
     * 查近 3 天所有正常帖子（FeedScoreJob 池重建用）
     */
    public List<Post> selectRecentPosts(OffsetDateTime since) {
        return postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getDeleted, 0)
                .eq(Post::getStatus, 1)
                .ge(Post::getCreatedAt, since)
                .orderByDesc(Post::getCreatedAt));
    }

    // ─── 批量 ───

    public List<Post> selectBatchIds(List<Long> postIds) {
        return postMapper.selectList(new LambdaQueryWrapper<Post>()
                .in(Post::getPostId, postIds));
    }

    public List<PostStat> selectStatsBatch(List<Long> postIds) {
        return postStatMapper.selectList(new LambdaQueryWrapper<PostStat>()
                .in(PostStat::getPostId, postIds));
    }
}
