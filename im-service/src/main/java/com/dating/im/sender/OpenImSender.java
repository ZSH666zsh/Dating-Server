package com.dating.im.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenIM 消息发送器。调 OpenIM REST /msg/send_msg 发送消息。
 *
 * <p>实现说明：im-service 出站消息，Phase 3 实现。
 * TODO: 替换为真实 OpenImApiClient.sendMsg() 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenImSender implements MessageSender {

    @Override
    public boolean supports(String provider) {
        return "openim".equals(provider);
    }

    @Override
    public boolean send(String fromUserId, String toUserId, String content, String messageType) {
        log.debug("OpenImSender.send: from={} to={} type={} contentLen={}",
                fromUserId, toUserId, messageType, content.length());
        // TODO: 调 OpenImApiClient.sendMsg(fromUserId, toUserId, content)
        return true;
    }
}
