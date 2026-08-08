package com.dating.im.model.event;

/** OpenIM callbackAfterSendSingleMsgCommand 的归一化事件。消息已发送成功。 */
public record MessageSentEvent(
        String provider,
        String messageId,
        long senderId,
        long receiverId,
        String content,
        String contentType,  // TEXT / IMAGE
        long sendTimeMs,
        String imageUrl      // IMAGE 类型时才有
) implements ImEvent {
    @Override public String getProvider() { return provider; }
}
