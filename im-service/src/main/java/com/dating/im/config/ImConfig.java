package com.dating.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * im-service 配置（Nacos 可热刷）。（扣费/反导流/AI/typing）
 * 对应 im-service-design.md §12，通过前缀 "im." 注入。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "im")
public class ImConfig {

    /** 聊天扣费开关 */
    private boolean chargeEnabled = true;
    /** 每条消息扣费金币数 */
    private int coinCost = 6;
    /** 异步扣费（默认 true） */
    private boolean async = true;

    /** 反导流检测开关 */
    private boolean antiFunnelEnabled = true;

    /** AI 回复分段配置 */
    private AiReply aiReply = new AiReply();
    /** typing 模拟配置 */
    private Typing typing = new Typing();
    /** 在线状态清扫配置 */
    private Presence presence = new Presence();

    @Data
    public static class AiReply {
        private int maxMessages = 4;
        private int minSegments = 3;
        private int minSplitChars = 30;
        private int perCharDelayMs = 45;
        private int minDelayMs = 300;
        private int maxDelayMs = 1500;
    }

    @Data
    public static class Typing {
        private int refreshIntervalMs = 3000;
        private int onsetDelayMinMs = 2000;
        private int onsetDelayMaxMs = 5000;
    }

    @Data
    public static class Presence {
        private boolean enabled = true;
        private int maxOnlineHours = 26;
        private String cron = "0 */30 * * * *";
    }
}
