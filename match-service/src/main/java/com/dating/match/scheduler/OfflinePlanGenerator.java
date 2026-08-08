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
 * DH 离线计划生成器。
 * 对应 match-service-prd-tech.md §6.3.2 OfflinePlanGenerator。
 *
 * <p>每 20 分钟处理一批离线超 20 分钟的 BH 用户，每人生成 3~6 个 DH like/visit 任务。
 * 用户打开 App 时看到"我不在的时候有人喜欢了我"。
 *
 * <p>实现说明：这是在 match-service 实现 DH Like/Visit 三个 Scheduler 时补充的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflinePlanGenerator {

    private final UserServiceClient userClient;
    private final DhInteractionTaskManager taskManager;
    private final SnowflakeIdGenerator idGenerator;
    private final CacheKeyBuilder keyBuilder;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 1_200_000) // 20 分钟
    public void generate() {
        // 获取离线 BH 用户
        // TODO: 接入 user:online:rank 后，取 score < (now - 20min) 的用户
        List<Long> offlineUserIds = getOfflineUserIds();

        int generated = 0;
        for (Long bhUserId : offlineUserIds) {
            // lastScene 检查：本次离线期内已生成过 OFFLINE 计划则跳过
            String lastScene = redisTemplate.opsForValue().get(keyBuilder.dhPlanLastScene(bhUserId));
            if ("OFFLINE".equals(lastScene)) continue;

            // 已有未执行的 OFFLINE 任务则跳过
            if (taskManager.hasPendingOnlineTask(bhUserId)) continue;

            // 获取 3~6 个 DH 候选（相比在线的BH少一些）
            int count = ThreadLocalRandom.current().nextInt(3, 7);
            var dhProfiles = userClient.listDhCandidates(0, 18, 100, 0, 100,
                    List.of(), List.of(), count);
            if (dhProfiles.isEmpty()) continue;

            List<DhInteractionTask> tasks = new ArrayList<>();
            for (var dh : dhProfiles) {
                boolean isLike = ThreadLocalRandom.current().nextDouble() < 0.4;
                DhInteractionTask task = new DhInteractionTask();
                task.setId(idGenerator.nextId());
                task.setFromUserId(dh.getUserId());
                task.setToUserId(bhUserId);
                task.setAction(isLike ? 1 : 2);
                task.setScene(2); // OFFLINE
                long delayMs = ThreadLocalRandom.current().nextLong(0, 30L * 60 * 1000);
                task.setExecuteTime(OffsetDateTime.now().plusNanos(delayMs * 1_000_000));
                if (isLike) task.setLikeContent("you seem interesting! ^_^");
                tasks.add(task);
            }
            taskManager.batchInsert(tasks);
            generated += tasks.size();

            // 标记 lastScene = OFFLINE 防重复
            redisTemplate.opsForValue().set(
                    keyBuilder.dhPlanLastScene(bhUserId), "OFFLINE", Duration.ofDays(7));
        }

        if (generated > 0) {
            log.info("OfflinePlanGenerator: generated {} tasks for {} users", generated, offlineUserIds.size());
        }
    }

    /**
     * 获取离线 BH 用户。
     * TODO: 接入 user:online:rank ZSet 后，ZRANGEBYSCORE -inf (now-20min)
     */
    private List<Long> getOfflineUserIds() {
        return List.of();
    }
}
