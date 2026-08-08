package com.dating.post.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis key 构建器 —— 所有 key 统一在这里拼，防止硬编码散落各处。
 *
 * 前缀从 application-dev.yml 的 app.cache.key-prefix 注入，
 * 不同学员用不同前缀（zhaoshihang），在共享 Redis 上互不干扰。
 */
@Component
public class CacheKeyBuilder {

    @Value("${app.cache.key-prefix}")
    private String prefix;

    // ─── 帖子详情缓存 ───

    /** Hash，字段为 PostDetailVO 的各属性 */
    public String postDetail(Long postId) {
        return prefix + ":post:detail:" + postId;
    }

    // ─── 写合并增量 ───

    /** 点赞未刷盘增量（String/Int） */
    public String postLikeIncr(Long postId) {
        return prefix + ":post:stat:incr:" + postId + ":likes";
    }

    /** 评论未刷盘增量（String/Int） */
    public String postCommentIncr(Long postId) {
        return prefix + ":post:stat:incr:" + postId + ":comments";
    }

    /** 待刷盘的 post_id 集合（Set） */
    public String postUpdatedSet() {
        return prefix + ":post:updated_set";
    }

    // ─── 评论 ZSet 窗口 ───

    /** 某帖子的最新 200 条评论 ID（ZSet，score=comment_id） */
    public String postComments(Long postId) {
        return prefix + ":post:comments:" + postId;
    }

    // ─── Feed 池 ───

    /** 冷启动池：按时间排序，新帖扶持。isMale=true → male 池 */
    public String coldStartPool(boolean isMale) {
        return prefix + ":feed:cold_start:pool:" + (isMale ? "male" : "female");
    }

    /** 热门推荐池：按热度分排序 */
    public String recommendPool(boolean isMale) {
        return prefix + ":feed:pool:recommend:" + (isMale ? "male" : "female");
    }

    /** 热门池临时 key（重建时影子写入，RENAME 原子切换） */
    public String recommendPoolTmp(boolean isMale) {
        return prefix + ":feed:pool:recommend:" + (isMale ? "male" : "female") + ":tmp";
    }

    /** 用户关注者时间线（写扩散） */
    public String userTimeline(Long userId) {
        return prefix + ":user:timeline:" + userId;
    }

    // ─── 布隆过滤器 ───

    /** 用户已读去重 BloomFilter */
    public String userReadBloom(Long userId) {
        return prefix + ":user:read:bloom:" + userId;
    }
}
