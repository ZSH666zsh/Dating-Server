package com.dating.post.service;

import com.dating.post.mq.PostFanoutMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 写扩散生产端：发帖后把 postId 异步推给关注者（经 RocketMQ，消费端见 PostFanoutConsumer）。
 *
 * <h3>为什么写扩散（Push 模式）？</h3>
 * 约会 App 关注数有上限（几百人），Push 的写入成本可控；
 * 读 Feed 时直接 ZREVRANGE 自己的 timeline 即可，毫秒级返回。
 * 如果读扩散（Pull），每次要查 N 个关注者的时间线再合并，慢得多。
 *
 * <h3>为什么用 MQ 而不是 @Async？</h3>
 * @Async 的消息在 JVM 内存 + 线程池里，进程崩溃消息就丢；
 * MQ Broker 落盘持久化，服务重启/崩溃后消息仍在，能重投 + 死信队列兜底。
 *
 * <h3>失败降级</h3>
 * 发送是 best-effort：MQ 连不上不阻塞发帖（发帖的 DB 三表写入才是权威）。
 * 该帖至少能从「全网热门池」和「冷启动池」被看到，5 分钟池重建兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostFanoutService {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${post.fanout.topic:dev_zhaoshihang_post_fanout_v1}")
    private String topic;

    /**
     * 发送写扩散消息。
     *
     * @param userId    发帖人
     * @param postId    新帖 ID
     * @param createdAt 发帖时间戳（毫秒）——作为 timeline ZSet 的 score，
     *                  由调用方一次性生成并随消息下发，重投时不变 → ZADD 幂等
     */
    public void fanoutToFollowers(Long userId, Long postId, Long createdAt) {
        try {
            PostFanoutMessage msg = new PostFanoutMessage(postId, userId, createdAt);
            rocketMQTemplate.convertAndSend(topic, msg);
            log.debug("Fanout message sent: topic={} postId={} owner={}", topic, postId, userId);
        } catch (Exception e) {
            // best-effort：MQ 挂了不阻塞发帖；可见性由热门池/冷启动池兜底
            log.warn("Failed to send fanout message, postId={} owner={}, err={}", postId, userId, e.getMessage());
        }
    }
}
