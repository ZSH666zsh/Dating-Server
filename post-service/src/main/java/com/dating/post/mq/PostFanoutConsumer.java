package com.dating.post.mq;

import com.dating.post.client.UserClient;
import com.dating.post.config.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 写扩散消费端：收到发帖消息后，把 postId 写进每个关注者的 timeline ZSet。
 *
 * <p>三个设计要点（面试常考）：</p>
 * <ol>
 *   <li><b>ZADD 幂等 = 重投安全</b>：score 用固定的发帖时间戳（{@link PostFanoutMessage#createdAt()}）。
 *       消息被 RocketMQ 重投多少次，ZADD 都是原地覆盖同一个 member，timeline 里不会出现重复 postId，
 *       消费端<b>无需建幂等表</b>。</li>
 *   <li><b>DLQ 兜底</b>：Redis 异常时让异常上抛 → RocketMQ 自动重投（默认 16 次）→ 仍失败进死信队列
 *       {@code %DLQ%{consumerGroup}}，隔离故障、不阻塞正常消费。</li>
 *   <li><b>timeline 有界</b>：每次写入后裁剪到最近 {@code post.fanout.timeline-max} 条（默认 100），
 *       用 {@code removeRangeByRank}（等价 ZREMRANGEBYRANK，单命令原子），防止单用户 timeline 无限膨胀。</li>
 * </ol>
 *
 * <p>关注者列表来自 user-service 的 getFriendUserIds（当前为桩返回空 → 本次写扩散 no-op，
 * 帖子仍可通过全网热门池 + 冷启动池被看到，5min 池重建兜底）。</p>
 *
 * <p>本地/无 RocketMQ 环境可通过 {@code post.fanout.enabled=false} 关掉消费端，避免连不上 Broker 阻塞启动。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "post.fanout.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${post.fanout.topic:dev_zhaoshihang_post_fanout_v1}",
        consumerGroup = "${post.fanout.consumer-group:dev_zhaoshihang_post_fanout_consumer}",
        selectorExpression = "*"
)
public class PostFanoutConsumer implements RocketMQListener<PostFanoutMessage> {

    private final UserClient userClient;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;

    /** timeline 上限（条），超出裁掉最老的 */
    @Value("${post.fanout.timeline-max:100}")
    private int timelineMax;

    /** 写进 timeline 的 TTL（天） */
    private static final long TIMELINE_TTL_DAYS = 7;

    @Override
    public void onMessage(PostFanoutMessage msg) {
        if (msg == null || msg.postId() == null) {
            log.warn("PostFanoutMessage is null/empty, skip");
            return;
        }
        // score 必须用固定的发帖时间戳；防御性兜底（正常不会 null）
        double score = msg.createdAt() != null ? msg.createdAt() : msg.postId();

        // 关注者列表（当前桩返回空 → no-op）
        List<Long> followerIds = userClient.getFriendUserIds(msg.ownerUserId());
        if (followerIds.isEmpty()) {
            log.debug("Fanout: no followers for owner={} postId={}, skip", msg.ownerUserId(), msg.postId());
            return;
        }

        for (Long followerId : followerIds) {
            String key = cacheKeyBuilder.userTimeline(followerId);
            // ZADD：score=固定发帖时间戳 → 重投幂等（覆盖语义，无重复 member）
            redisTemplate.opsForZSet().add(key, String.valueOf(msg.postId()), score);
            // 裁剪到 timelineMax：移除 rank [0, size-max)，即最老的部分（removeRange 即 ZREMRANGEBYRANK，单命令原子）
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size > timelineMax) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - timelineMax - 1);
            }
            redisTemplate.expire(key, TIMELINE_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Fanout to follower={} postId={} score={}", followerId, msg.postId(), score);
        }
    }
}
