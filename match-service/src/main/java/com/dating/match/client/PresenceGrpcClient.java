package com.dating.match.client;

import com.dating.zhaoshihang.proto.im.ListRecentOfflineUsersRequest;
import com.dating.zhaoshihang.proto.im.PresenceServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * im-service PresenceService gRPC 客户端。
 * 用于 DH 离线互动计划：取"最近下线的用户"，给其生成模拟 like/visit。
 *
 * <p>跨服务边界：在线/离线状态归 im-service 所有，本服务经 gRPC 查询，
 * 不直连 im-service 的 Redis（红线 3）。im-service 不可用时降级返回空，
 * 离线 DH 计划 no-op，不影响主流程。
 */
@Slf4j
@Component
public class PresenceGrpcClient {

    @GrpcClient("im-service")
    private PresenceServiceGrpc.PresenceServiceBlockingStub presenceStub;

    /**
     * 查询 [sinceMs, untilMs] 窗口内下线的用户 ID。
     */
    public List<Long> getRecentOfflineUserIds(long sinceMs, long untilMs, int limit) {
        try {
            var req = ListRecentOfflineUsersRequest.newBuilder()
                    .setSince(sinceMs)
                    .setUntil(untilMs)
                    .setLimit(limit)
                    .build();
            var resp = presenceStub.listRecentOfflineUsers(req);
            return resp.getUserIdsList();
        } catch (StatusRuntimeException e) {
            log.warn("PresenceGrpcClient.listRecentOfflineUsers failed, return empty", e);
            return List.of();
        } catch (Exception e) {
            log.warn("PresenceGrpcClient error, return empty", e);
            return List.of();
        }
    }
}
