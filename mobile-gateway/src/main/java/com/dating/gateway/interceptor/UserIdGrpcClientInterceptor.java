package com.dating.gateway.interceptor;

import io.grpc.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * gRPC 客户端拦截器 —— 将当前登录用户的 userId / deviceId 注入 gRPC Metadata。
 *
 * <p>JwtAuthFilter 解析 token 后把 userId 放进了 request attribute，
 * 这里在每次 gRPC 调用前读出来，塞进 metadata（x-user-id / x-device-id），
 * 下游服务的 GrpcServerInterceptor 收到后注入 Context。
 *
 * <p>对应 mobile-gateway-design.md §5.6 下游 gRPC client 矩阵。
 */
@Slf4j
@GrpcGlobalClientInterceptor
public class UserIdGrpcClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DEVICE_ID_KEY =
            Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACE_ID_KEY =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 从当前 HTTP 请求上下文读取 userId / deviceId
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes sra) {
                    HttpServletRequest request = sra.getRequest();
                    Long userId = (Long) request.getAttribute("userId");
                    String deviceId = (String) request.getAttribute("deviceId");

                    if (userId != null && userId > 0) {
                        headers.put(USER_ID_KEY, String.valueOf(userId));
                    }
                    if (deviceId != null && !deviceId.isEmpty()) {
                        headers.put(DEVICE_ID_KEY, deviceId);
                    }
                }
                super.start(responseListener, headers);
            }
        };
    }
}
