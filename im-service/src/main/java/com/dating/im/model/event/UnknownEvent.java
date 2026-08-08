package com.dating.im.model.event;

/** 不识别的回调事件 —— 记录日志后放行。 */
public record UnknownEvent(String provider, String rawCommand) implements ImEvent {
    @Override public String getProvider() { return provider; }
}
