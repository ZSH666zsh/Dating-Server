package com.dating.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * user-service 启动入口。
 *
 * 提供三大能力（见 1ARCHITECTURE.md §6）：
 * 1. 身份解析（手机号/第三方/设备登录）
 * 2. 档案管理（onboarding + 编辑 + 头像）
 * 3. 智能召回（DH/BH 候选）
 *
 * 端口：HTTP :8081（调试用 REST）/ gRPC :9091
 * 与 post-service(:8080/:9090) 错开，本机可同时跑两个服务。
 */
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
