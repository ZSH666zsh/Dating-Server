package com.dating.match.scheduler;

import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.entity.DhInteractionTask;
import com.dating.match.entity.LikeRecord;
import com.dating.match.entity.VisitRecord;
import com.dating.match.manager.DhInteractionTaskManager;
import com.dating.match.manager.LikeRecordManager;
import com.dating.match.manager.VisitRecordManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DH 互动任务执行器。
 * 对应 match-service-prd-tech.md §6.3.3 LikeVisitorTaskExecutor。
 *
 * <p>每 1 分钟扫描到期任务，执行后硬删。
 * LIKE → upsert like_record；
 * VISIT → upsert visit_record。
 *
 * <p>实现说明：这是在 match-service 实现 DH Like/Visit 三个 Scheduler 时补充的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeVisitorTaskExecutor {

    private final DhInteractionTaskManager taskManager;
    private final LikeRecordManager likeRecordManager;
    private final VisitRecordManager visitRecordManager;
    private final SnowflakeIdGenerator idGenerator;

    @Scheduled(fixedDelay = 60_000)
    public void execute() {

        // 扫描到期任务（execute_time <= now，最多 500 条）
        List<DhInteractionTask> tasks = taskManager.scanDueTasks(500);
        if (tasks.isEmpty()) return;

        int success = 0;
        for (DhInteractionTask task : tasks) {
            try {
                executeSingle(task);
                taskManager.deleteById(task.getId());  // 硬删已执行任务
                success++;
            } catch (Exception e) {
                log.error("Failed to execute DH task id={}", task.getId(), e);
            }
        }

        log.info("LikeVisitorTaskExecutor: executed {}/{} tasks", success, tasks.size());
    }

    // 对每条任务：
    @Transactional(rollbackFor = Exception.class)
    void executeSingle(DhInteractionTask task) {
        if (task.getAction() == 1) {
            // LIKE→ upsert like_record
            LikeRecord record = new LikeRecord();
            record.setId(idGenerator.nextId());
            record.setFromUserId(task.getFromUserId());
            record.setToUserId(task.getToUserId());
            record.setFromUserType(2); // DH
            record.setSource(task.getScene() == 1 ? 2 : 3); // DH_PLAN_ONLINE=2, DH_PLAN_OFFLINE=3
            record.setLikeContent(task.getLikeContent());
            record.setLikedAt(OffsetDateTime.now());
            likeRecordManager.upsert(record);
        } else {
            // VISIT → upsert visit_record
            VisitRecord record = new VisitRecord();
            record.setId(idGenerator.nextId());
            record.setFromUserId(task.getFromUserId());
            record.setToUserId(task.getToUserId());
            record.setFromUserType(2); // DH
            record.setSource(task.getScene() == 1 ? 2 : 3);
            record.setVisitedAt(OffsetDateTime.now());
            visitRecordManager.upsert(record);
        }
    }
}
