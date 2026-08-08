package com.dating.im.service;

import com.dating.im.model.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 事件分发器。对应 im-service-design.md §4.1 switch(event)。
 * 按 event 类型路由到对应 handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImEventDispatcher {

    private final BeforeSendHandler beforeSendHandler;
    private final MessageSentHandler messageSentHandler;
    private final PresenceService presenceService;

    /**
     * 分发事件，返回响应码。0=放行，非0=拒发（仅 before-send 可拒）。
     */
    public int dispatch(ImEvent event) {
        if (event == null) return 0;

        return switch (event) {
            case MessageBeforeSendEvent e -> beforeSendHandler.handle(e);
            case MessageSentEvent e -> { messageSentHandler.handle(e); yield 0; }
            case UserOnlineEvent e -> { presenceService.online(e); yield 0; }
            case UserOfflineEvent e -> { presenceService.offline(e); yield 0; }
            case UnknownEvent e -> {
                log.debug("Unknown event: provider={} cmd={}", e.getProvider(), e.rawCommand());
                yield 0;
            }
        };
    }
}
