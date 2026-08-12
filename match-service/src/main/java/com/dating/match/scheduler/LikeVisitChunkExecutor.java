package com.dating.match.scheduler;

import com.dating.match.entity.DhInteractionTask;
import com.dating.match.entity.LikeRecord;
import com.dating.match.entity.VisitRecord;
import com.dating.match.manager.DhInteractionTaskManager;
import com.dating.match.manager.LikeRecordManager;
import com.dating.match.manager.VisitRecordManager;
import com.dating.match.service.InteractionNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DH 互动任务"一批"的执行器（多线程分片后，每个工作线程各执行一批）。
 *
 * <p>事务边界：@Transactional 是<b>线程绑定</b>的，多线程并行时不能由主线程统一包事务，
 * 所以把"批量 upsert + 删除"抽成独立 Bean 的 @Transactional 方法，每个工作线程各自起一个事务。
 * 任一批失败 → 该批回滚、任务保留，下一轮重扫自动重试（批量 ON CONFLICT 幂等，重试安全）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeVisitChunkExecutor {

    private final LikeRecordManager likeRecordManager;
    private final VisitRecordManager visitRecordManager;
    private final DhInteractionTaskManager taskManager;
    private final InteractionNotificationService notifier;

    /**
     * 执行一批 DH 互动任务：按 LIKE/VISIT 分组批量 UPSERT，成功后批量删除。
     *
     * @return 本批处理的任务数
     */
    @Transactional(rollbackFor = Exception.class)
    public int executeChunk(List<DhInteractionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0;

        List<LikeRecord> likes = new ArrayList<>();
        List<VisitRecord> visits = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>(tasks.size());

        for (DhInteractionTask task : tasks) {
            taskIds.add(task.getId());
            if (task.getAction() == 1) {
                likes.add(toLikeRecord(task));
            } else {
                visits.add(toVisitRecord(task));
            }
        }

        // 每条 action 各一条批量 SQL（原 N 次逐条 upsert → 2 次）
        if (!likes.isEmpty()) likeRecordManager.batchUpsert(likes);
        if (!visits.isEmpty()) visitRecordManager.batchUpsert(visits);
        // 执行成功才删除：失败则本批回滚，任务保留重试
        taskManager.deleteByIds(taskIds);

        // 互动通知与写库异步解耦：事务提交后才触发 @Async 通知（未读计数 + 最近列表），
        // 不占调度器关键路径 IO；本批事务回滚则通知不触发（避免"通知发了、记录没写"的脏状态）。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifier.notifyBatch(likes, visits);
            }
        });

        log.debug("LikeVisitChunkExecutor: chunk done, likes={} visits={}", likes.size(), visits.size());
        return tasks.size();
    }

    private LikeRecord toLikeRecord(DhInteractionTask task) {
        LikeRecord record = new LikeRecord();
        record.setFromUserId(task.getFromUserId());
        record.setToUserId(task.getToUserId());
        record.setFromUserType(2); // DH
        record.setSource(task.getScene() == 1 ? 2 : 3); // DH_PLAN_ONLINE=2, DH_PLAN_OFFLINE=3
        record.setLikeContent(task.getLikeContent());
        record.setLikedAt(OffsetDateTime.now());
        return record;
    }

    private VisitRecord toVisitRecord(DhInteractionTask task) {
        VisitRecord record = new VisitRecord();
        record.setFromUserId(task.getFromUserId());
        record.setToUserId(task.getToUserId());
        record.setFromUserType(2); // DH
        record.setSource(task.getScene() == 1 ? 2 : 3);
        record.setVisitedAt(OffsetDateTime.now());
        return record;
    }
}
