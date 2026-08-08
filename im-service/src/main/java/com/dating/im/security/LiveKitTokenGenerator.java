package com.dating.im.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * LiveKit 通话 Token 生成器。HS256 JWT。
 * 对应 im-service-design.md §9 GenerateCallToken。
 */
@Slf4j
@Component
public class LiveKitTokenGenerator {

    @Value("${livekit.api-key:}")
    private String apiKey;

    @Value("${livekit.api-secret:}")
    private String apiSecret;

    /** 生成 1v1 通话 token，TTL 30 分钟。 */
    public String generateToken(long userId, long peerId) {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            log.warn("LiveKit not configured, using stub token");
            return "livekit_stub_token";
        }

        String room = "call_" + UUID.randomUUID().toString().substring(0, 8);
        SecretKey key = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .issuer(apiKey)
                .subject(String.valueOf(userId))
                .claim("video", Map.of(
                        "room", room,
                        "roomJoin", true,
                        "canPublish", true,
                        "canSubscribe", true
                ))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(1800))) // 30min
                .signWith(key)
                .compact();
    }
}
