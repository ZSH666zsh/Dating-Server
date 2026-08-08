package com.dating.im.model.event;

/** OpenIM callbackBeforeSendSingleMsgCommand 的归一化事件。sender 即将发消息，可拦截。 */
public record MessageBeforeSendEvent(
        String provider,
        String messageId,
        long senderId,
        long receiverId,
        String content,
        String contentType    // TEXT / IMAGE / ...
) implements ImEvent {
    @Override public String getProvider() { return provider; }
}
