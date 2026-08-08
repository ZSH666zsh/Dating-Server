package com.dating.post.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 写扩散服务：发帖时异步把 post_id 推送给所有关注者的 timeline。
 *
 * <h3>为什么写扩散（Push 模式）？</h3>
 * 约会 App 关注数有上限（几百人），Push 的写入成本可控。
 * 读 Feed 时直接 ZREVRANGE 自己的 timeline 即可，毫秒级返回。
 * 如果读扩散（Pull），每次要查 N 个关注者的时间线再合并，慢得多。
 *
 * <h3>失败降级</h3>
 * user-service 不可用 → getFriendUserIds 返空 → 本次写扩散 no-op。
 * 该帖至少能从「全网热门池」和「冷启动池」被看到，5 分钟池重建兜底。
 *
 * <h3>TODO：等 user-service 就绪后替换</h3>
 * <pre>{@code
 * // 假代码，真实现时取消注释：
 * List<Long> followers = userClient.getFriendUserIds(userId);
 * for (Long follower : followers) {
 *     String key = cacheKeyBuilder.userTimeline(follower);
 *     redisTemplate.opsForZSet().add(key, postId.toString(), (double) System.currentTimeMillis());
 *     // 裁剪到 100 条
 *     Long size = redisTemplate.opsForZSet().zCard(key);
 *     if (size != null && size > 100) {
 *         redisTemplate.opsForZSet().removeRangeByRank(key, 0, size - 101);
 *     }
 *     redisTemplate.expire(key, 7, TimeUnit.DAYS);
 * }
 * }</pre>
 */
@Slf4j
@Service
public class PostFanoutService {

    /**
     * 异步写扩散。
     * 当前是桩实现（no-op），因为 user-service 还没提供 getFriendUserIds。
     *
     * 当前 getFriendUserIds 返回空列表，所以写扩散是桩实现。等好友关系系统上线后再激活。
     *
     * @param userId 发帖人
     * @param postId 新帖子 ID
     */
    @Async
    public void     fanoutToFollowers(Long userId, Long postId) {
        log.debug("Fanout stub: userId={} postId={} (no-op, waiting for user-service)", userId, postId);
        // TODO: 调 userClient.getFriendUserIds → ZADD 每个关注者的 timeline（见类注释）
    }
}
