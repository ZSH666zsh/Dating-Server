package com.dating.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * gRPC 客户端配置。
 * gateway 自身不暴露 gRPC，只通过 @GrpcClient 调用下游。
 * 客户端由 grpc-spring-boot-starter 自动配置 + Nacos 服务发现。
 */
@Configuration
public class GrpcClientConfig {
    // gRPC client beans auto-configured by grpc-spring-boot-starter
}
