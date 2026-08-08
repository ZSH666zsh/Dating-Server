package com.dating.im.model.event;

/** OpenIM callbackUserOnlineCommand —— 用户上线。 */
public record UserOnlineEvent(String provider, long userId, int platform, long timestampMs) implements ImEvent {
    @Override public String getProvider() { return provider; }
}
