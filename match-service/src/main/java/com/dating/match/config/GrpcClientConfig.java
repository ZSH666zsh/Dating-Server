package com.dating.match.config;

import org.springframework.context.annotation.Configuration;

/**
 * gRPC 客户端配置，@GrpcClient} 注解自动注入，无需手动创建 Bean。
 * UserServiceClient / PaymentServiceClient / ImServiceClient 通过
 *
 * @see com.dating.match.client.UserServiceClient
 */
@Configuration
public class GrpcClientConfig {
    // gRPC 客户端由 grpc-spring-boot-starter 自动配置
}
