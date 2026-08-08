package com.dating.im.model.event;

/** OpenIM callbackUserOfflineCommand —— 用户下线。 */
public record UserOfflineEvent(String provider, long userId, long timestampMs) implements ImEvent {
    @Override public String getProvider() { return provider; }
}
