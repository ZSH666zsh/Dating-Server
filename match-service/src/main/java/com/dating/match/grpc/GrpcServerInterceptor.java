package com.dating.match.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * gRPC 服务端拦截器 —— 从 Metadata 提取 x-user-id 注入 Context。
 *
 * <p>mobile-gateway 解 JWT 后把 userId 塞进 gRPC Metadata（key=x-user-id），
 * 本拦截器在每个 RPC 入口提取出来，后续业务代码通过 {@link #getCurrentUserId()} 获取。
 */
@GrpcGlobalServerInterceptor
public class GrpcServerInterceptor implements ServerInterceptor {

    private static final Context.Key<Long> USER_ID_KEY = Context.key("userId");
    private static final Metadata.Key<String> USER_ID_MD_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String userIdStr = headers.get(USER_ID_MD_KEY);
        Context context = Context.current();
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                context = context.withValue(USER_ID_KEY, Long.parseLong(userIdStr));
            } catch (NumberFormatException ignored) {}
        }
        return Contexts.interceptCall(context, call, headers, next);
    }

    /** 获取当前请求的 userId */
    public static Long getCurrentUserId() {
        Long userId = USER_ID_KEY.get();
        return userId != null ? userId : 0L;
    }
}
