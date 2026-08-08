package com.dating.gateway.security;

import com.dating.gateway.config.CacheKeyBuilder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器。
 * 白名单路径跳过鉴权，其余请求从 Authorization header 提取 Bearer token 校验。
 * 校验后将 userId 写入请求属性（下游代码从请求头 X-User-Id 读取）。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private final JwtIssuer jwtIssuer;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /** 白名单路径（不鉴权） */
    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/send-sms-code",
            "/api/v1/auth/login-phone",
            "/api/v1/auth/login-third-party",
            "/api/v1/auth/login-device",
            "/api/v1/auth/refresh",
            "/health",
            "/actuator/health"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 白名单放行
        if (isWhitelisted(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":10501,\"message\":\"Missing or invalid token\"}");
            return;
        }

        String token = authHeader.substring(7);
        var parsed = jwtIssuer.parseAccessToken(token);
        if (parsed == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":10501,\"message\":\"Token invalid or expired\"}");
            return;
        }

        // 检查黑名单
        String blacklistKey = keyBuilder.blacklist(parsed.jti());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":10503,\"message\":\"Token revoked\"}");
            return;
        }

        // 将 userId 注入请求属性（下游 Controller 通过 @RequestHeader("X-User-Id") 读取）
        request.setAttribute("userId", parsed.userId());
        request.setAttribute("deviceId", parsed.deviceId());

        chain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(w -> {
            if (w.endsWith("/**")) return path.startsWith(w.substring(0, w.length() - 3));
            return path.equals(w);
        });
    }
}
