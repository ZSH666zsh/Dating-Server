package com.dating.match.service;

import com.dating.match.constant.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DH 延迟匹配服务（进程内 TaskScheduler 调度）。
 * 对应 match-service-prd-tech.md §5.2 DH 延迟匹配设计。
 *
 * <p>用户右划 DH 后延迟 15s-2min 再创建 match，模拟真人回应节奏。
 * 重启服务会丢失 in-flight 任务，属于设计上接受的 trade-off。
 */
@Slf4j
@Service
public class DhDelayedMatchService {

    private final MatchService matchService;
    private final ThreadPoolTaskScheduler taskScheduler;

    // ThreadPoolTaskScheduler 是进程内的，重启后尚未执行的任务就丢了。
    // 这是设计接受的：不是每个右划都必须 match，丢了等下次 D1 或 D0 重建即可。

    public DhDelayedMatchService(MatchService matchService) {
        this.matchService = matchService;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(4);
        this.taskScheduler.setThreadNamePrefix("dh-delay-");
        this.taskScheduler.initialize();
    }

    /**
     * 调度一个延迟匹配任务。
     * @param userId 划卡用户
     * @param dhId   目标 DH
     */
    public void scheduleDelayedMatch(Long userId, Long dhId) {

        // 随机 15 秒到 2 分钟之间，模拟真人的回应节奏
        long delayMs = ThreadLocalRandom.current().nextLong(15_000, 120_001);
        Instant executeAt = Instant.now().plusMillis(delayMs);

        taskScheduler.schedule(() -> {
            try {
                Long matchId = matchService.createMatch(userId, dhId, Source.SWIPE_MATCH);
                log.info("DH delayed match executed: userId={} dhId={} matchId={} delayMs={}",
                        userId, dhId, matchId, delayMs);
            } catch (Exception e) {
                log.error("DH delayed match failed: userId={} dhId={}", userId, dhId, e);
            }
        }, executeAt);

        log.debug("DH delayed match scheduled: userId={} dhId={} delayMs={}", userId, dhId, delayMs);
    }
}
