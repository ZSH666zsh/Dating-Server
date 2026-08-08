package com.dating.post.config;

import org.springframework.context.annotation.Configuration;

/**
 * 跨服务调用配置。
 *
 * gRPC 客户端由 UserClient 上的 {@code @GrpcClient("user-service")} 自动注入，
 * 通过 Nacos 服务发现（discovery:///user-service）连接到 user-service:9091。
 *
 * 之前这里创建了 RestTemplate Bean 做 REST 过渡调用，违反了红线 #3。
 *
 * @see com.dating.post.client.UserClient
 */
@Configuration
public class GrpcClientConfig {
    // gRPC 客户端由 @GrpcClient 注解 + grpc-spring-boot-starter 自动配置
    // 无需手动创建 Bean
}
