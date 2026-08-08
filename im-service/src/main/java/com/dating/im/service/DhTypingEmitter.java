package com.dating.im.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * DH 正在输入发射器。对应 im-service-design.md §5.2。
 * AI 生成回复期间持续向 BH 下发 typing 通知，模拟真人输入节奏。
 */
@Slf4j
@RequiredArgsConstructor
public class DhTypingEmitter implements AutoCloseable {

    private final long dhUserId;
    private final long bhUserId;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> typingTask;
    private volatile boolean closed;

    /**
     * 启动 typing 发射。
     * 先等 2~5 秒模拟阅读时间，然后每 3 秒续发一次。
     */
    public void start() {
        int onsetDelay = ThreadLocalRandom.current().nextInt(2000, 5001);
        scheduler.schedule(() -> {
            if (!closed) {
                sendTyping();
                typingTask = scheduler.scheduleAtFixedRate(
                        this::sendTyping, 3000, 3000, TimeUnit.MILLISECONDS);
            }
        }, onsetDelay, TimeUnit.MILLISECONDS);
    }

    private void sendTyping() {
        // TODO: 调 OpenImSender 发 typing 通知
        log.trace("Typing: DH={} → BH={}", dhUserId, bhUserId);
    }

    @Override
    public void close() {
        closed = true;
        if (typingTask != null) typingTask.cancel(false);
        scheduler.shutdownNow();
        log.debug("Typing stopped: DH={} BH={}", dhUserId, bhUserId);
    }

    /** 在两段回复之间发一帧 typing（无配套 stop）。 */
    public void ping() {
        if (!closed) sendTyping();
    }

    /**
     * 创建一个 TypingEmitter 实例。使用 try-with-resources 自动停止：
     * <pre>{@code
     * try (var emitter = DhTypingEmitter.create(dhId, bhId)) {
     *     emitter.start();
     *     String reply = aiChatClient.chat(threadId, message);
     * }
     * }</pre>
     */
    public static DhTypingEmitter create(long dhUserId, long bhUserId) {
        return new DhTypingEmitter(dhUserId, bhUserId);
    }
}
