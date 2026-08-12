package com.dating.match.scheduler;

import com.dating.match.entity.DhInteractionTask;
import com.dating.match.manager.DhInteractionTaskManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DH 互动任务执行器（多线程分片 + 批量 UPSERT）。
 * 对应 match-service-prd-tech.md §6.3.3 LikeVisitorTaskExecutor。
 *
 * <h3>优化思路（单次 ScheduleTask 吞吐提升）</h3>
 * <ol>
 *   <li><b>多线程获取数据 + 并行分片</b>：一次扫描最多 {@value #SCAN_LIMIT} 条到期任务，
 *       按 {@value #PARALLELISM} 线程切分成若干批，提交到固定线程池并行执行，
 *       摊薄单线程的 DB IO 等待；</li>
 *   <li><b>批量 UPSERT</b>：每批内部按 LIKE/VISIT 分组，各一条
 *       {@code INSERT ... ON CONFLICT DO UPDATE}，单批 IO 从 N 次降到 2 次；</li>
 *   <li><b>失败重试 + 幂等</b>：每批一个独立事务（见 {@link LikeVisitChunkExecutor}），
 *       失败回滚、任务保留，下一轮重扫自动重试；ON CONFLICT + 唯一约束保证重试不产生脏数据。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeVisitorTaskExecutor {

    /** 单次扫描上限（多线程后放宽，吞吐从 500/次 提到 SCAN_LIMIT/次） */
    private static final int SCAN_LIMIT = 2000;
    /** 并行度 */
    private static final int PARALLELISM = 4;
    /** 等待所有分片完成的超时 */
    private static final long AWAIT_TIMEOUT_SEC = 30;

    private final DhInteractionTaskManager taskManager;
    private final LikeVisitChunkExecutor chunkExecutor;

    private final ExecutorService taskPool = Executors.newFixedThreadPool(PARALLELISM);

    @Scheduled(fixedDelay = 60_000)
    public void execute() {
        // 1. 扫描到期任务（一次最多 SCAN_LIMIT 条）
        List<DhInteractionTask> tasks = taskManager.scanDueTasks(SCAN_LIMIT);
        if (tasks.isEmpty()) return;

        // 2. 切成 PARALLELISM 批
        int chunkSize = Math.max(1, (tasks.size() + PARALLELISM - 1) / PARALLELISM);
        List<List<DhInteractionTask>> chunks = partition(tasks, chunkSize);

        // 3. 并行提交到线程池，CountDownLatch 等待全部结束（超时兜底，不阻塞下一个调度周期）
        CountDownLatch latch = new CountDownLatch(chunks.size());
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (List<DhInteractionTask> chunk : chunks) {
            taskPool.submit(() -> {
                try {
                    chunkExecutor.executeChunk(chunk);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("LikeVisitorTaskExecutor chunk failed, {} tasks kept for retry", chunk.size(), e);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("LikeVisitorTaskExecutor: chunks={} success={} failed={} total={}",
                chunks.size(), success.get(), failed.get(), tasks.size());
    }

    private List<List<DhInteractionTask>> partition(List<DhInteractionTask> tasks, int chunkSize) {
        List<List<DhInteractionTask>> chunks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i += chunkSize) {
            chunks.add(tasks.subList(i, Math.min(i + chunkSize, tasks.size())));
        }
        return chunks;
    }

    @PreDestroy
    public void shutdown() {
        taskPool.shutdown();
    }
}
