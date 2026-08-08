package com.dating.gateway.security;

import com.dating.gateway.config.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Token 黑名单管理器。
 * 登出时将 access token 的 jti 加入 Redis 黑名单（TTL = 剩余有效期）。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistManager {

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 将 token 加入黑名单。
     * @param jti       token ID
     * @param expiresAt token 过期时间（黑名单 TTL 与其同步）
     */
    public void blacklist(String jti, Instant expiresAt) {
        String key = keyBuilder.blacklist(jti);
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
        }
    }
}
