package com.dating.im.service;

import com.dating.im.client.OpenImApiClient;
import com.dating.im.client.UserServiceGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务通知服务。封装 OpenIM /msg/send_business_notification。
 * 对应 im-service-design.md §8.1。
 *
 * <p>key 常量：typing / match_success / match_welcome。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final OpenImApiClient openImApiClient;
    private final UserServiceGrpcClient userClient;

    /** 发送业务通知。 */
    public boolean send(long fromUserId, long toUserId, String key, String dataJson,
                         boolean persist, int reliability) {
        boolean ok = openImApiClient.sendBusinessNotification(fromUserId, toUserId, key, dataJson);
        log.debug("Notification: key={} from={} to={} persist={} ok={}",
                key, fromUserId, toUserId, persist, ok);
        return ok;
    }

    /** 匹配成功通知（双方各发一条）。对应 im-service-design.md §8.2。 */
    public void notifyMatchSuccess(long matchId, long userA, long userB, long matchedAtMs) {
        String payload = "{\"matchId\":" + matchId + ",\"matchedAt\":" + matchedAtMs + "}";
        log.info("MatchSuccess: matchId={} userA={} userB={}", matchId, userA, userB);

        // 给 userA 发
        send(userB, userA, "match_success", payload, false, 2);

        // 给 userB 发（如果 userA 不是 DH）
        Boolean isDh = userClient.getUserType(userA) == 2;
        if (!Boolean.TRUE.equals(isDh)) {
            send(userA, userB, "match_success", payload, false, 2);
        }
    }
}
