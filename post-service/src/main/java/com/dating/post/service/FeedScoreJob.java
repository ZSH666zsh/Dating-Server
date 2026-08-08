package com.dating.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Feed 池重建 Job：每 5 分钟触发一次全网热门池重建。
 *
 * 实际重建逻辑委托给 {@link FeedService#rebuildRecommendPool()}，
 * 本 Job 只做定时触发和 ShedLock 互斥。
 *
 * <h3>为什么 5 分钟？</h3>
 * Hacker News 公式有 (hoursDiff + 2)^1.5 时间衰减项，
 * 帖子分数随时间被动下跌，不能靠"增量更新"维护。
 * 5 分钟全量重建一次，用户不感知排序变化。
 *
 * <h3>失败兜底</h3>
 * Job 卡住 → 池仍是上次成功重建的快照，读侧不报错。
 * 监控 feed.score.rebuild.duration 报警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedScoreJob {

    private final FeedService feedService;

    /**
     * 每 5 分钟重建全网热门池
     */
    @Scheduled(fixedRate = 300_000) // 5 分钟
    @SchedulerLock(name = "post.feedScoreRebuild",
            lockAtMostFor = "PT10M",  // 兜底：10 分钟后锁自动释放
            lockAtLeastFor = "PT10S") // 防抖：至少持锁 10 秒
    public void rebuildRecommendPool() {
        feedService.rebuildRecommendPool();
    }
}
