package com.dating.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis key 构建器。
 * 格式：gateway:auth:{domain}:{id}
 */
@Component
public class CacheKeyBuilder {

    @Value("${app.cache.key-prefix:zhaoshihang}")
    private String prefix;

    /** SMS 验证码：gateway:auth:sms:code:{phoneE164} */
    public String smsCode(String phoneE164) {
        return prefix + ":gateway:auth:sms:code:" + phoneE164;
    }

    /** SMS 冷却：gateway:auth:sms:cooldown:{phoneE164} */
    public String smsCooldown(String phoneE164) {
        return prefix + ":gateway:auth:sms:cooldown:" + phoneE164;
    }

    /** Token 黑名单：gateway:auth:blacklist:{jti} */
    public String blacklist(String jti) {
        return prefix + ":gateway:auth:blacklist:" + jti;
    }
}
