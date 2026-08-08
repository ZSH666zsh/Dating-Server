package com.dating.payment.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class GrpcServerInterceptor implements ServerInterceptor {

    private static final Context.Key<Long> USER_ID_KEY = Context.key("userId");
    private static final Metadata.Key<String> USER_ID_MD_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String userIdStr = headers.get(USER_ID_MD_KEY);
        Context context = Context.current();
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try { context = context.withValue(USER_ID_KEY, Long.parseLong(userIdStr)); }
            catch (NumberFormatException ignored) {}
        }
        return Contexts.interceptCall(context, call, headers, next);
    }

    public static Long getCurrentUserId() {
        Long id = USER_ID_KEY.get();
        return id != null ? id : 0L;
    }
}
