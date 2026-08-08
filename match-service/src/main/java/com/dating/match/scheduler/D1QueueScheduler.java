package com.dating.match.scheduler;

import com.dating.match.client.UserServiceClient;
import com.dating.match.mapper.UserSwipeHistoryMapper;
import com.dating.match.service.D1QueueService;
import com.dating.match.entity.UserSwipeHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * D1 日更队列调度器。
 * 对应 match-service-prd-tech.md §4.2 D1 日更队列。
 *
 * <p>每日 UTC 07:00（美东 EDT 03:00 / EST 02:00）触发。
 * 为昨天有划卡行为的用户离线生成个性化推荐队列。
 *
 * <p>实现说明：这是在 match-service 实现 D1 日更队列功能时补充的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class D1QueueScheduler {

    private final D1QueueService d1QueueService;
    private final UserSwipeHistoryMapper swipeHistoryMapper;
    private final UserServiceClient userClient;

    @Scheduled(cron = "0 0 7 * * *", zone = "UTC")
    public void runDailyQueueGen() {
        log.info("D1QueueScheduler started at {}", OffsetDateTime.now());

        // 查昨天有划卡行为的用户
        LocalDate yesterday = LocalDate.now().minusDays(1);
        OffsetDateTime yesterdayStart = yesterday.atStartOfDay(OffsetDateTime.now().getOffset()).toOffsetDateTime();
        OffsetDateTime yesterdayEnd = yesterdayStart.plusDays(1);

        var activeUsers = swipeHistoryMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserSwipeHistory>()
                                .select(UserSwipeHistory::getUserId)
                                .ge(UserSwipeHistory::getSwipedAt, yesterdayStart)
                                .lt(UserSwipeHistory::getSwipedAt, yesterdayEnd)
                                .groupBy(UserSwipeHistory::getUserId))
                .stream()
                .map(UserSwipeHistory::getUserId)
                .distinct()
                .toList();

        log.info("D1QueueScheduler: {} active users yesterday", activeUsers.size());

        int successCount = 0;
        for (Long userId : activeUsers) {
            try {
                Boolean isMale = userClient.isMale(userId);
                int gender = Boolean.TRUE.equals(isMale) ? 1 : 2;
                int cards = d1QueueService.generateForUser(userId, gender);
                if (cards > 0) successCount++;
            } catch (Exception e) {
                log.error("D1 queue generation failed for userId={}", userId, e);
            }
        }

        log.info("D1QueueScheduler completed: {}/{} users updated", successCount, activeUsers.size());
    }
}
