package com.dating.im.service;

import com.dating.im.client.UserServiceGrpcClient;
import com.dating.im.entity.ChatMessage;
import com.dating.im.model.event.MessageSentEvent;
import com.dating.im.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * After-send 处理器 —— 消息已发出之后的处理。
 * 对应 im-service-design.md §4.3。
 *
 * <p>按路由类型处理：
 * - BH→BH：只落库
 * - BH→DH：落库 + AI 回复（调 ai-chat）
 * - DH→BH：只落库（AI 回复本身已发出）
 * - DH→DH：异常，落库 + 跳过
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSentHandler {

    private final ChatMessageRepository messageRepo;
    private final UserServiceGrpcClient userClient;
    private final AiReplyDispatcher aiReplyDispatcher;

    public void handle(MessageSentEvent event) {
        // 查 sender 和 receiver 的 user_type
        Integer fromType = userClient.getUserType(event.senderId());
        Integer toType = userClient.getUserType(event.receiverId());

        String routeType = buildRouteType(fromType, toType);
        log.debug("MessageSent: route={} msgId={}", routeType, event.messageId());

        // 落库
        saveMessage(event, routeType);

        // BH→DH：走 AI 回复链路
        if ("BH_DH".equals(routeType) && "TEXT".equals(event.contentType())) {
            aiReplyDispatcher.dispatch(event);
        }
    }

    private String buildRouteType(Integer fromType, Integer toType) {
        String from = (fromType != null && fromType == 2) ? "DH" : "BH";
        String to = (toType != null && toType == 2) ? "DH" : "BH";
        return from + "_" + to;
    }

    private void saveMessage(MessageSentEvent event, String routeType) {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId("openim_" + event.messageId());
        msg.setFromUserId(event.senderId());
        msg.setToUserId(event.receiverId());
        msg.setContent(event.content());
        msg.setType(event.contentType());
        msg.setProvider(event.getProvider());
        msg.setRouteType(routeType);
        msg.setTimestamp(event.sendTimeMs() / 1000);
        messageRepo.save(msg);
    }
}
