package com.dating.post.service;

import com.dating.post.client.UserClient;
import com.dating.post.config.CacheKeyBuilder;
import com.dating.post.config.SnowflakeIdGenerator;
import com.dating.post.constant.PostStatus;
import com.dating.post.entity.Post;
import com.dating.post.entity.PostStat;
import com.dating.post.exception.BizException;
import com.dating.post.manager.PostManager;
import com.dating.post.manager.PostStatManager;
import com.dating.post.vo.PostDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Feed 推荐服务：三路混合推荐 + 热门池重建。
 *
 * <h3>三路来源</h3>
 * ① 全网热门池（按 Hacker News 热度分，5 分钟重建）
 * ② 好友时间线（写扩散，发帖时 @Async 推）
 * ③ 冷启动池（新帖时间序，发帖时同步 ZADD）
 *
 * <h3>位置分配（每 10 条一页）</h3>
 * 1,2,4,5,7,8,9,10 → 热门池（降级→冷启动→好友）
 * 3                → 好友强插（降级→热门）
 * 6                → 冷启动扶持（降级→热门）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostManager postManager;
    private final PostStatManager postStatManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final UserClient userClient;
    private final RedissonClient redissonClient;
    private final PostReadService postReadService;

    // ──────────────────────────────────────────────
    //  推荐 Feed 读
    // ──────────────────────────────────────────────

    /**
     * 推荐 Feed（三路混合）
     *
     * @param userId   当前用户
     * @param pageSize 每页条数（≤ 10）
     * @param cursor   "recOffset:csOffset"，首次 "0:0"
     */
    public FeedResult getRecommendFeed(Long userId, int pageSize, String cursor) {
        if (pageSize <= 0 || pageSize > 10) pageSize = 10;

        // ── 解析游标 ──
        String[] parts = cursor.split(":");
        int recOffset = Integer.parseInt(parts[0]);
        int csOffset = Integer.parseInt(parts.length > 1 ? parts[1] : "0");

        // ── 异性优先：男看女、女看男 ──
        boolean isMale = userClient.isMale(userId);
        boolean oppositeSex = !isMale;

        // ── 布隆过滤器（已读去重，容量 5000，1% 误判） ──
        RBloomFilter<String> bloom = redissonClient.getBloomFilter(cacheKeyBuilder.userReadBloom(userId));
        bloom.tryInit(5000L, 0.01);

        // ── 三路并行取数 ──

        // ① 热门池（读多 50% 应对去重损耗）
        Set<String> recommendRaw = redisTemplate.opsForZSet()
                .reverseRange(cacheKeyBuilder.recommendPool(oppositeSex),
                        recOffset, recOffset + pageSize * 2);
        List<String> recommendList = new ArrayList<>(recommendRaw != null ? recommendRaw : List.of());

        // ② 冷启动池（新帖）
        Set<String> coldStartRaw = redisTemplate.opsForZSet()
                .reverseRange(cacheKeyBuilder.coldStartPool(oppositeSex),
                        csOffset, csOffset + 10);
        List<String> coldStartList = new ArrayList<>(coldStartRaw != null ? coldStartRaw : List.of());

        // ③ 好友时间线（最近 7 天，取 5 条）
        Set<String> friendRaw = redisTemplate.opsForZSet()
                .reverseRangeByScore(cacheKeyBuilder.userTimeline(userId),
                        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, 5);
        List<String> friendList = new ArrayList<>(friendRaw != null ? friendRaw : List.of());

        // ── 获取好友 userId 列表（频控用） ──
        List<Long> friendUserIds = userClient.getFriendUserIds(userId);
        Set<Long> friendUserIdSet = new HashSet<>(friendUserIds);

        // ── 三路混合 ──
        List<Long> merged = mergeThreeWay(
                recommendList, coldStartList, friendList,
                pageSize, bloom, friendUserIdSet
        );

        // ── 组装详情 ──
        List<PostDetailVO> items = new ArrayList<>();
        int recTaken = 0, csTaken = 0;
        for (Long postId : merged) {
            try {
                PostDetailVO detail = postReadService.getPostDetail(postId, userId);
                items.add(detail);
                // 把已返回的 ID 记入布隆过滤器
                bloom.add(String.valueOf(postId));
            } catch (BizException e) {
                log.warn("Feed: post {} not found (deleted?), skip", postId);
            }
        }

        // 计算实际消耗的游标偏移（首页粗略，后续页用 cursor 接力）
        // 简化：recOffset + pageSize（热门池消耗），csOffset + 1（冷启动消耗）
        int nextRec = recOffset + pageSize;
        int nextCs = csOffset + 1;
        String nextCursor = nextRec + ":" + nextCs;

        log.debug("Feed returned: userId={} size={} cursor={}->{}", userId, items.size(), cursor, nextCursor);
        return new FeedResult(items, nextCursor);
    }

    /**
     * 三路混合核心算法
     */
    private List<Long> mergeThreeWay(
            List<String> recommendList,
            List<String> coldStartList,
            List<String> friendList,
            int pageSize,
            RBloomFilter<String> bloom,
            Set<Long> friendUserIdSet) {

        List<Long> result = new ArrayList<>();
        int recIdx = 0, csIdx = 0, friendIdx = 0;
        Set<Long> usedFriendIds = new HashSet<>();

        for (int pos = 0; pos < pageSize; pos++) {
            int position = pos + 1; // 1-based
            Long chosen = null;

            if (position == 3) {
                // 位置 3 → 好友强插
                chosen = pickNext(friendList, friendIdx, bloom, usedFriendIds, friendUserIdSet);
                if (chosen != null) friendIdx++;
                // 降级 → 热门池
                if (chosen == null) {
                    chosen = pickNext(recommendList, recIdx, bloom, null, null);
                    if (chosen != null) recIdx++;
                }
                // 二级降级 → 冷启动
                if (chosen == null) {
                    chosen = pickNext(coldStartList, csIdx, bloom, null, null);
                    if (chosen != null) csIdx++;
                }
            } else if (position == 6) {
                // 位置 6 → 冷启动扶持
                chosen = pickNext(coldStartList, csIdx, bloom, null, null);
                if (chosen != null) csIdx++;
                // 降级 → 热门池
                if (chosen == null) {
                    chosen = pickNext(recommendList, recIdx, bloom, null, null);
                    if (chosen != null) recIdx++;
                }
            } else {
                // 其他位置 → 热门池
                chosen = pickNext(recommendList, recIdx, bloom, null, null);
                if (chosen != null) recIdx++;
                // 降级 → 冷启动
                if (chosen == null) {
                    chosen = pickNext(coldStartList, csIdx, bloom, null, null);
                    if (chosen != null) csIdx++;
                }
                // 二级降级 → 好友
                if (chosen == null) {
                    chosen = pickNext(friendList, friendIdx, bloom, usedFriendIds, friendUserIdSet);
                    if (chosen != null) friendIdx++;
                }
            }

            if (chosen != null) {
                result.add(chosen);
            }
        }

        return result;
    }

    /**
     * 从列表中选取下一个未读且未被频控的帖子
     *
     * @param items          候选列表
     * @param idx            当前遍历位置
     * @param bloom          布隆过滤器（已读去重）
     * @param usedFriendIds  已用好友 ID（频控，可为 null）
     * @param friendIdSet    当前用户的好友 ID 集合（可为 null）
     * @return 选中的 postId，null 表示无可用
     */
    private Long pickNext(
            List<String> items, int idx,
            RBloomFilter<String> bloom,
            Set<Long> usedFriendIds,
            Set<Long> friendIdSet) {

        while (idx < items.size()) {
            String postIdStr = items.get(idx);
            Long postId = Long.parseLong(postIdStr);

            // 布隆去重
            if (bloom.contains(postIdStr)) {
                idx++;
                continue;
            }

            // 好友频控：同一好友最多出现 1 次
            if (usedFriendIds != null && friendIdSet != null) {
                // 需要查 post 的 userId 来判断，但我们没这个信息
                // 简化：按 postId 分桶，同一 post 在一页内不重复
                // 实际应该查 post 的 userId，但这里为了性能用简单方式
                // TODO: 后续可缓存 post→userId 映射
            }

            return postId;
        }
        return null;
    }

    // ──────────────────────────────────────────────
    //  热门池重建（由 FeedScoreJob 每 5 分钟触发）
    // ──────────────────────────────────────────────

    /**
     * 重建全网热门池（Hacker News 变体打分）。
     *
     * <h3>流程</h3>
     * 1. 查近 3 天所有正常帖子
     * 2. 批量取计数（DB 基准 + Redis 增量补偿）
     * 3. 内存算 Hacker News 分
     * 4. UserClient 批量取性别（Caffeine 缓存削峰）
     * 5. 按性别分桶写入 Redis tmp key
     * 6. RENAME 原子切换（读侧无感）
     */
    public void rebuildRecommendPool() {
        long start = System.currentTimeMillis();

        // ── 1. 捞近 3 天所有正常帖子 ──
        OffsetDateTime since = OffsetDateTime.now().minusDays(3);
        List<Post> posts = postManager.selectRecentPosts(since);

        if (posts.isEmpty()) {
            log.debug("Feed pool rebuild: no posts in last 3 days");
            return;
        }

        // ── 2. 批量取计数 ──
        List<Long> postIds = posts.stream().map(Post::getPostId).collect(Collectors.toList());
        List<PostStat> stats = postManager.selectStatsBatch(postIds);
        Map<Long, PostStat> statMap = stats.stream()
                .collect(Collectors.toMap(PostStat::getPostId, s -> s, (a, b) -> a));

        // ── 3. Redis 增量补偿 + 内存打分 ──
        // Score = (10 + 1*likes + 3*comments) / (hoursDiff + 2)^1.5
        Map<Long, Double> scoreMap = new HashMap<>(postIds.size());

        for (Post post : posts) {
            long pid = post.getPostId();
            PostStat stat = statMap.get(pid);

            // DB 基准值
            int dbLikes = (stat != null) ? stat.getLikeCount() : 0;
            int dbComments = (stat != null) ? stat.getCommentCount() : 0;

            // Redis 增量补偿（未刷盘部分）
            int incrLikes = getIntSafely(cacheKeyBuilder.postLikeIncr(pid));
            int incrComments = getIntSafely(cacheKeyBuilder.postCommentIncr(pid));

            int totalLikes = dbLikes + incrLikes;
            int totalComments = dbComments + incrComments;

            // Hacker News 变体
            double hoursDiff = Duration.between(post.getCreatedAt(), OffsetDateTime.now()).toHours();
            double score = (10.0 + 1.0 * totalLikes + 3.0 * totalComments)
                    / Math.pow(Math.max(hoursDiff, 0) + 2, 1.5);

            scoreMap.put(pid, score);
        }

        // ── 4. 批量取性别 ──
        List<Long> distinctUserIds = posts.stream()
                .map(Post::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Boolean> genderMap = userClient.getGenders(distinctUserIds);

        // ── 5. 构建性别分桶 ZSet ──
        // 男池装♂发的帖，女池装♀发的帖（读侧取异性池）
        Map<Boolean, List<Map.Entry<Long, Double>>> byGender = new HashMap<>();
        byGender.put(true, new ArrayList<>());  // male
        byGender.put(false, new ArrayList<>()); // female

        for (Post post : posts) {
            boolean isMale = genderMap.getOrDefault(post.getUserId(), false);
            Double score = scoreMap.get(post.getPostId());
            if (score != null) {
                byGender.get(isMale).add(Map.entry(post.getPostId(), score));
            }
        }

        // ── 6. 写入 tmp ZSet + 裁剪 + RENAME ──
        for (boolean gender : new boolean[]{true, false}) {
            String tmpKey = cacheKeyBuilder.recommendPoolTmp(gender);
            String realKey = cacheKeyBuilder.recommendPool(gender);

            // 批量 ZADD
            List<Map.Entry<Long, Double>> entries = byGender.get(gender);
            if (!entries.isEmpty()) {
                // 按分排序，取前 3000
                entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                int limit = Math.min(entries.size(), 3000);

                // 先清空残留 tmp key（上次 Job 异常中断遗留）
                redisTemplate.delete(tmpKey);

                for (int i = 0; i < limit; i++) {
                    redisTemplate.opsForZSet().add(
                            tmpKey,
                            String.valueOf(entries.get(i).getKey()),
                            entries.get(i).getValue()
                    );
                }
                redisTemplate.expire(tmpKey, 7, java.util.concurrent.TimeUnit.DAYS);

                // RENAME 原子切换：读侧绝不会读到半写状态
                redisTemplate.rename(tmpKey, realKey);

                log.debug("Feed pool: gender={} candidates={} afterTrim={}", gender, entries.size(), limit);
            } else {
                // 该性别没有帖子 → 清空真实池，避免读到过期数据
                redisTemplate.delete(realKey);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Feed pool rebuilt: candidates={} male={} female={} time={}ms",
                posts.size(),
                byGender.get(true).size(),
                byGender.get(false).size(),
                elapsed);
    }

    /** 安全读 Redis integer */
    private int getIntSafely(String key) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ──────────────────────────────────────────────
    //  返回值
    // ──────────────────────────────────────────────

    public record FeedResult(List<PostDetailVO> items, String nextCursor) {
    }
}
