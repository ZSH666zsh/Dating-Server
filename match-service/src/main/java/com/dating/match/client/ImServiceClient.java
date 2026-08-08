package com.dating.match.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * im-service gRPC 客户端（桩实现）。
 * 对应 match-service-prd-tech.md §7.7。
 *
 * <p>TODO: im-service 上线后替换为真实 gRPC 调用。
 * 当前接口：
 * - EnsureConversation(userIdA, userIdB) → ok
 * - SendSystemMessage(toUserId, payload) → ok
 * - TriggerDhOpening(dhUserId, targetUserId) → ok
 */
@Slf4j
@Component
public class ImServiceClient {

    /**
     * 确保两个用户之间存在 IM 会话（幂等）。
     */
    public boolean ensureConversation(Long userIdA, Long userIdB) {
        log.debug("ImServiceClient.ensureConversation(stub) {} <-> {}", userIdA, userIdB);
        return true;
    }

    /**
     * 发送系统消息。
     */
    public boolean sendSystemMessage(Long toUserId, String title, String content) {
        log.debug("ImServiceClient.sendSystemMessage(stub) to={} title={}", toUserId, title);
        return true;
    }

    /**
     * 触发 DH 开场白（匹配后 ai-chat 生成首条消息）。
     */
    public boolean triggerDhOpening(Long dhUserId, Long targetUserId) {
        log.debug("ImServiceClient.triggerDhOpening(stub) dh={} target={}", dhUserId, targetUserId);
        return true;
    }

    /**
     * 发送配对成功通知（match_success）。
     */
    public boolean notifyMatchSuccess(Long matchId, Long userA, Long userB) {
        log.debug("ImServiceClient.notifyMatchSuccess(stub) matchId={} {} <-> {}", matchId, userA, userB);
        return true;
    }
}
