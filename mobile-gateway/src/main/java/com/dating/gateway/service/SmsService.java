package com.dating.gateway.service;

import com.dating.gateway.config.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信验证码服务。
 * mock 模式：固定返回 "123456"。
 */
@Service
@RequiredArgsConstructor
public class SmsService {

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    @Value("${app.sms.mock-code:123456}")
    private String mockCode;

    /**
     * 下发验证码并存入 Redis。
     * @return 验证码（mock 模式固定 "123456"）
     */
    public String issueCode(String phoneE164) {
        // 检查冷却
        String cooldownKey = keyBuilder.smsCooldown(phoneE164);
        String remaining = redisTemplate.opsForValue().get(cooldownKey);
        if (remaining != null) {
            throw new RuntimeException("请60秒后再试");
        }

        // 生成验证码（mock 模式固定）
        String code = mockCode;
        if ("000000".equals(mockCode)) {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        }

        // 存入 Redis 5 分钟
        String codeKey = keyBuilder.smsCode(phoneE164);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));

        // 冷却 60 秒
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));

        return code;
    }

    /**
     * 校验验证码。
     */
    public boolean verifyCode(String phoneE164, String code) {
        String codeKey = keyBuilder.smsCode(phoneE164);
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) return false;
        boolean match = stored.equals(code);
        if (match) {
            redisTemplate.delete(codeKey); // 一次性
        }
        return match;
    }
}
