package com.dating.im.sender;

/** 消息发送器接口（出站）。对应 im-service-design.md §3 provider 抽象。 */
public interface MessageSender {
    boolean supports(String provider);
    boolean send(String fromUserId, String toUserId, String content, String messageType);
}
