package com.dating.im.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/** gRPC 服务端拦截器 —— 从 Metadata 取 x-user-id 注入 Context。 */
@GrpcGlobalServerInterceptor
public class GrpcServerInterceptor implements ServerInterceptor {
    private static final Context.Key<Long> USER_ID_KEY = Context.key("userId");
    private static final Metadata.Key<String> USER_ID_MD =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String id = headers.get(USER_ID_MD);
        Context ctx = Context.current();
        if (id != null && !id.isEmpty()) {
            try { ctx = ctx.withValue(USER_ID_KEY, Long.parseLong(id)); }
            catch (NumberFormatException ignored) {}
        }
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
