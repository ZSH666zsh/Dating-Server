package com.dating.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mobile-gateway 启动类。
 *
 * Vibe App BFF 网关：REST→gRPC 协议转换 + JWT 鉴权 + BFF 聚合。
 *
 * 使用虚拟线程（spring.threads.virtual.enabled=true）。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
