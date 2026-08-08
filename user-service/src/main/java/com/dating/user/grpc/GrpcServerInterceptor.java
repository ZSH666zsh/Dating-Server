package com.dating.user.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * gRPC 服务端拦截器 —— 从 gRPC Metadata 中提取调用方的 userId，注入到 gRPC Context。
 *
 * <h3>为什么需要这个拦截器？</h3>
 * <p>在项目架构中，userId 不能在 proto request body 中传递，
 * 因为如果下游服务的每个 RPC 请求体都带 userId，客户端就可以随意伪造他人的 userId。
 * 正確做法是：userId 由 mobile-gateway 的 {@code UserIdGrpcClientInterceptor} 从 JWT 解析后，
 * 放入 gRPC 请求的 Metadata（key = "x-user-id"），
 * 然后本拦截器在服务端从 Metadata 中取出，放入 gRPC 的 Context，
 * 业务代码通过 {@link #getCurrentUserId()} 静态方法获取。
 *
 * <h3>传递链路</h3>
 * <pre>
 * [App 请求] → mobile-gateway JwtAuthFilter 解 JWT
 *   → request.setAttribute("userId", uid)
 *   → UserIdGrpcClientInterceptor 读取 attribute
 *   → 写入 gRPC Metadata x-user-id
 *
 *   ───── gRPC 调用（Metadata 随请求发出）─────→
 * [user-service] GrpcServerInterceptor（本类）:
 *   → 读取 Metadata 中的 x-user-id
 *   → 存入 gRPC Context（线程安全的上下文）
 *   → 业务代码调 getCurrentUserId() 即可获取
 * </pre>
 *
 * <h3>为什么每个 gRPC 服务都要有这个？</h3>
 * <p>因为 gRPC 不同于 HTTP——它没有 Servlet 容器，没有 RequestAttributes。
 * 所以 userId 不能像 REST 那样用 @RequestHeader 或 RequestContextHolder 取。
 * 每个暴露 gRPC 端口的服务都必须有这样一个拦截器，
 * 负责从 Metadata 中读取 userId 并保存到 gRPC Context 中。
 *
 * @see <a href="1ARCHITECTURE.md §10.1">gRPC 的 userId 传递机制</a>
 */
@GrpcGlobalServerInterceptor
public class GrpcServerInterceptor implements ServerInterceptor {

    /**
     * gRPC Context key，用于存储当前请求的 userId。
     * Context 是 gRPC 的线程级上下文容器（类似 ThreadLocal），
     * 同一次 gRPC 调用的所有代码都能通过这个 key 读取。
     */
    private static final Context.Key<Long> USER_ID_KEY = Context.key("userId");

    /**
     * Metadata key，对应 HTTP/2 头部的 x-user-id 字段。
     * 这是 gateway 在发出 gRPC 请求时放入的，
     * 服务端从这里解析出 userId。
     */
    private static final Metadata.Key<String> USER_ID_MD_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * 拦截 gRPC 请求，在请求到达业务处理代码之前执行。
     *
     * 1. 从请求的 Metadata 中取出 x-user-id
     * 2. 如果存在，就放入 gRPC Context（withValue 创建一个新的 Context，
     *    继承原 Context 的值，同时附加 userId）
     * 3. 用 Contexts.interceptCall() 将携带 userId 的 Context 传递给后续处理链
     *    ——这样业务代码在任何地方调 getCurrentUserId() 都能拿到
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        // 1. 从 HTTP/2 头部 Metadata 中读取 x-user-id 字符串
        String userIdStr = headers.get(USER_ID_MD_KEY);
        Context context = Context.current();           // 当前线程的 Context

        // 2. 如果 x-user-id 存在且非空，转为 Long 类型并存入 Context
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                // withValue() 创建子 Context，继承父 Context 所有属性 + 附加 userId
                context = context.withValue(USER_ID_KEY, Long.parseLong(userIdStr));
            } catch (NumberFormatException ignored) {
                // 如果 x-user-id 不是合法数字，忽略（userId 用默认值 0）
            }
        }

        // 3. 把携带了 userId 的 Context 传递给实际的服务处理器链
        //    Contexts.interceptCall 会确保这个 Context 在整个请求生命周期中可用
        return Contexts.interceptCall(context, call, headers, next);
    }

    /**
     * 业务代码通过这个静态方法获取当前请求的调用方 userId。
     *
     * 用法示例：
     * <pre>
     * Long callerId = GrpcServerInterceptor.getCurrentUserId();
     * </pre>
     *
     * 注意：这个方法只能在 gRPC 请求线程中调用，
     * 如果在 @Async 异步线程中调用会返回 0（Context 不跨线程传播）。
     * 需要用参数传递 userId，或者手动 propagate Context。
     *
     * @return 当前请求的 userId，如果没有（比如调用方没传）则返回 0L
     */
    public static Long getCurrentUserId() {
        Long userId = USER_ID_KEY.get();  // 从当前线程的 Context 取值
        return userId != null ? userId : 0L;
    }
}
