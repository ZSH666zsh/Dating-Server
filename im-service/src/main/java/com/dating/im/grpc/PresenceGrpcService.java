package com.dating.im.grpc;

import com.dating.im.service.PresenceService;
import com.dating.zhaoshihang.proto.im.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * PresenceService gRPC 实现。对应 im-service-design.md §7.3。
 * 给 match-service 查询在线/离线用户列表。
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PresenceGrpcService extends PresenceServiceGrpc.PresenceServiceImplBase {

    private final PresenceService presenceService;

    @Override
    public void listOnlineUserIds(ListOnlineUserIdsRequest req, StreamObserver<ListOnlineUserIdsResponse> resp) {
        try {
            var userIds = presenceService.listOnlineUserIds(req.getSince(), req.getUntil(), req.getLimit());
            resp.onNext(ListOnlineUserIdsResponse.newBuilder()
                    .addAllUserIds(userIds)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listOnlineUserIds error", e);
            resp.onError(Status.INTERNAL.asRuntimeException());
        }
    }

    @Override
    public void listRecentOfflineUsers(ListRecentOfflineUsersRequest req, StreamObserver<ListRecentOfflineUsersResponse> resp) {
        try {
            var userIds = presenceService.listRecentOfflineUsers(req.getSince(), req.getUntil(), req.getLimit());
            resp.onNext(ListRecentOfflineUsersResponse.newBuilder()
                    .addAllUserIds(userIds)
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("listRecentOfflineUsers error", e);
            resp.onError(Status.INTERNAL.asRuntimeException());
        }
    }
}
