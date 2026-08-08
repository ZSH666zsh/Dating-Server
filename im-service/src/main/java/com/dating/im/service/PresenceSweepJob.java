package com.dating.im.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 孤儿会话清扫定时任务。每 30 分钟执行。对应 im-service-design.md §7.2。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceSweepJob {

    private final PresenceService presenceService;

    @Scheduled(cron = "0 */30 * * * *")
    public void sweep() {
        presenceService.sweepOrphans();
    }
}
