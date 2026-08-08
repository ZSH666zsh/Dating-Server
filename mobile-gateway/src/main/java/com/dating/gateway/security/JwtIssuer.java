package com.dating.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发器。用 HMAC-SHA256（HS256）对称签名。
 *
 * 生产环境密钥从 Nacos 配置 app.jwt.secret 注入，不写死在代码里。
 *
 * @see JwtProperties
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtIssuer {

    private final JwtProperties properties;

    /** 签名密钥（懒加载） */
    private Key signingKey;

    private Key getSigningKey() {
        if (signingKey == null) {
            byte[] keyBytes;
            // 1) 优先从 properties 读 key（Nacos 配置注入）
            if (properties.getSecret() != null && !properties.getSecret().isEmpty()) {
                keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
            } else {
                // 2) 无配置时用默认值（本地开发用，Nacos 配置后自动覆盖）
                String devSecret = "zhaoshihang-dev-jwt-secret-key-20260715-min-256bits!!";
                log.warn("JWT secret not configured via app.jwt.secret, using dev default. "
                        + "Set Nacos config 'app.jwt.secret' for production.");
                keyBytes = devSecret.getBytes(StandardCharsets.UTF_8);
            }
            // 确保 key 至少 256 bits
            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                keyBytes = padded;
            }
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    /**
     * 签发 access token。
     */
    public String issueAccessToken(Long userId, String deviceId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())  // jti（黑名单用）
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim("did", deviceId)
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60L)))
                .signWith(getSigningKey())  // HS256 对称签名
                .compact();
    }

    /**
     * 签发 refresh token（opaque 随机字符串，非 JWT）。
     */
    public String issueRefreshToken() {
        byte[] bytes = new byte[32];
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 解析 access token。
     */
    public ParsedToken parseAccessToken(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith((SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedToken(
                    claims.getId(),
                    Long.parseLong(claims.getSubject()),
                    claims.get("did", String.class),
                    claims.getExpiration().toInstant()
            );
        } catch (Exception e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    public record ParsedToken(String jti, Long userId, String deviceId, Instant expiresAt) {}
}
