package com.dating.im.model.event;

/**
 * ImEvent sealed 接口 —— 所有 OpenIM 回调事件的归一化模型。
 * 对应 im-service-design.md §4.1 事件分发。
 *
 * <p>实现说明：im-service 回调处理，Phase 3 实现。
 */
public sealed interface ImEvent
        permits MessageBeforeSendEvent, MessageSentEvent, UserOnlineEvent, UserOfflineEvent, UnknownEvent {
    String getProvider();
}
