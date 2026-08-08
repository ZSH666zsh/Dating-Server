package com.dating.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置。
 *
 * <p>密钥从 Nacos 配置注入（app.jwt.secret），不在代码里硬编码，不入 git。
 * 启动后通过环境变量或 Nacos 配置 app.jwt.secret 提供。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** access token 有效期（分钟） */
    private int accessTokenTtlMinutes = 15;

    /** refresh token 有效期（天） */
    private int refreshTokenTtlDays = 7;

    /**
     * HMAC-SHA256 签名密钥（至少 32 字符）。
     * 从 Nacos 配置 app.jwt.secret 注入，不入 git。
     * 本地开发可在 IDE Run Configuration 设环境变量或 application-dev.yml 中配置。
     */
    private String secret;

    /** 签发者 */
    private String issuer = "dating-mobile-gateway";
}
