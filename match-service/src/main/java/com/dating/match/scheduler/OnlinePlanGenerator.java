package com.dating.match.scheduler;

import com.dating.match.client.UserServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.entity.DhInteractionTask;
import com.dating.match.manager.DhInteractionTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DH 在线计划生成器。
 * 对应 match-service-prd-tech.md §6.3.1。
 *
 * <p>每 1 分钟扫描在线 BH 用户，为每人生成 5~10 个 DH like/visit 任务。
 * 任务执行时间在 [now, now + 120min] 内均匀随机分布。
 *
 * <p>实现说明：这是在 match-service 实现 DH Like/Visit 三个 Scheduler 时补充的。
 * 当前 getUserIdsByRecentActivity() 为简化实现，等 user-service Heartbeat RPC
 * 上线后接入 user:online:rank ZSet。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlinePlanGenerator {

    private final UserServiceClient userClient;
    private final DhInteractionTaskManager taskManager;
    private final SnowflakeIdGenerator idGenerator;
    private final CacheKeyBuilder keyBuilder;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void generate() {
        // 获取活跃 BH 用户（简化：使用最近有划卡行为的用户作为"在线"代理）
        List<Long> activeUserIds = getUserIdsByRecentActivity();

        int generated = 0;
        for (Long bhUserId : activeUserIds) {
            // cooldown 检查：2h 内不重复生成
            String cooldownKey = keyBuilder.dhPlanCooldown(bhUserId);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
                continue;
            }
            // 已有未执行的 ONLINE 任务则跳过
            if (taskManager.hasPendingOnlineTask(bhUserId)) {
                continue;
            }

            // 获取 5~10 个 DH 候选
            int count = ThreadLocalRandom.current().nextInt(5, 11);
            var dhProfiles = userClient.listDhCandidates(0, 18, 100, 0, 100,
                    List.of(), List.of(), count);
            if (dhProfiles.isEmpty()) continue;

            // 每个 DH 生成一个任务，40% LIKE / 60% VISIT
            List<DhInteractionTask> tasks = new ArrayList<>();
            for (var dh : dhProfiles) {
                boolean isLike = ThreadLocalRandom.current().nextDouble() < 0.4;
                DhInteractionTask task = new DhInteractionTask();
                task.setId(idGenerator.nextId());
                task.setFromUserId(dh.getUserId());
                task.setToUserId(bhUserId);
                task.setAction(isLike ? 1 : 2);
                task.setScene(1); // ONLINE
                long delayMs = ThreadLocalRandom.current().nextLong(0, 120L * 60 * 1000);
                task.setExecuteTime(OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
                if (isLike) task.setLikeContent("nice profile! 😊");
                tasks.add(task);
            }
            taskManager.batchInsert(tasks);
            generated += tasks.size();

            // 写 cooldown（2h）
            redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofHours(2));
            // 记录 lastScene（ONLINE）
            redisTemplate.opsForValue().set(
                    keyBuilder.dhPlanLastScene(bhUserId), "ONLINE", Duration.ofDays(7));
        }

        if (generated > 0) {
            log.info("OnlinePlanGenerator: generated {} tasks for {} users",
                    generated, activeUserIds.size());
        }
    }

    /**
     * 获取活跃 BH 用户。
     * getUserIdsByRecentActivity() 返回空列表，所以实际上没有生成任何任务。
     * 需要等 user-service 的在线状态接口上线。
     *
     * TODO: 接入 user-service Heartbeat + user:online:rank ZSet 后改为 ZRANGEBYSCORE 查询
     */
    private List<Long> getUserIdsByRecentActivity() {
        return List.of();
    }
}
