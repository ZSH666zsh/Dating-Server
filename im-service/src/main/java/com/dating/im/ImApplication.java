package com.dating.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * im-service 启动类。
 * OpenIM + LiveKit 编排中枢：消息路由 / AI 回复 / 付费聊天 / 反导流 / 在线状态。
 *
 * <p>实现说明：按 im-service-design.md 完整实现，Phase 3 完成。
 * 核心链路：
 *   1. 回调收口：OpenIM → gateway → onRawCallback → 事件分发 → 扣费/反导流/AI
 *   2. 出站通知：match-service gRPC → sendBusinessNotification → OpenIM
 *   3. 在线状态：OpenIM 上下线回调 → Redis ZSet + PG 会话
 *   4. Token：GetImToken / GenerateCallToken → OpenIM / LiveKit
 */
@SpringBootApplication
@EnableScheduling
public class ImApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImApplication.class, args);
    }
}
