package com.dating.im.service;

import com.dating.im.config.ImConfig;
import com.dating.im.model.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 回复编排器。对应 im-service-design.md §5.3。
 * 分段 + 打字节奏 + 逐条发送 + 落库。
 *
 * <p>实现说明：ai-chat（Python）未就绪时静默跳过，不阻断消息主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReplyDispatcher {

    private final AiChatGrpcClient aiChatClient;
    private final ImConfig config;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 编排 AI 回复流程（异步）。
     * 1. typing 开始 → 阅读延迟
     * 2. 调 ai-chat 生成回复
     * 3. 分句 + 打字节奏逐条发回
     */
    public void dispatch(MessageSentEvent event) {
        String threadId = event.senderId() + ":" + event.receiverId();
        long dhUserId = event.receiverId();  // 消息发给 DH，DH 是回复方
        long bhUserId = event.senderId();

        executor.submit(() -> {
            try (var typing = DhTypingEmitter.create(dhUserId, bhUserId)) {
                typing.start();

                // 调 ai-chat 生成回复
                String reply = aiChatClient.chat(threadId, event.content());
                if (reply == null || reply.isBlank()) {
                    log.debug("AI reply unavailable for threadId={}", threadId);
                    return;
                }

                // 分句
                var splitter = new SentenceSplitter();
                var segments = splitter.split(reply, config.getAiReply().getMaxMessages());
                boolean multiSegment = segments.size() >= config.getAiReply().getMinSegments()
                        && reply.length() >= config.getAiReply().getMinSplitChars();

                if (!multiSegment) {
                    // 直接合并发送
                    doSend(dhUserId, bhUserId, reply, event.messageId(), 0, 0);
                } else {
                    // 逐条发送（带打字节奏）
                    for (int i = 0; i < segments.size(); i++) {
                        if (i > 0) {
                            typing.ping();
                            int delay = Math.min(
                                    config.getAiReply().getMaxDelayMs(),
                                    Math.max(config.getAiReply().getMinDelayMs(),
                                            segments.get(i).length() * config.getAiReply().getPerCharDelayMs()));
                            Thread.sleep(delay);
                        }
                        doSend(dhUserId, bhUserId, segments.get(i), event.messageId(), i + 1, segments.size());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("AI reply interrupted for threadId={}", threadId);
            } catch (Exception e) {
                log.error("AI reply failed for threadId={}", threadId, e);
            }
        });
    }

    /**
     * 发送单条回复。
     * messageId 规则：单条 <原id>_ai，多条 <原id>_ai_<idx>
     */
    private void doSend(long from, long to, String content, String origMsgId, int idx, int total) {
        String msgId = total <= 1 ? origMsgId + "_ai" : origMsgId + "_ai_" + idx;
        log.debug("AI reply send: from={} to={} msgId={} contentLen={}", from, to, msgId, content.length());
        // TODO: 调 OpenImSender 发消息 + ChatMessageRecorder 落库
    }
}
