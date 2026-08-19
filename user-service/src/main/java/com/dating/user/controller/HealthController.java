package com.dating.user.controller;

import com.dating.user.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;

/**
 * 健康检查接口（探活用）。
 *
 * 同时充当存活 + 就绪探针：进程存活且 MySQL/Redis 依赖可用才返回 UP。
 * 供容器（K8s liveness/readiness）、负载均衡与运维排障调用。
 * 不依赖认证（不读取 X-User-Id），任何请求头均可访问。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.application.name:user-service}")
    private String serviceName;

    @Value("${app.version:1.0.0}")
    private String version;

    /**
     * GET /health
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean mysqlOk = checkMysql();
        boolean redisOk = checkRedis();
        boolean up = mysqlOk && redisOk;
        String status = up ? "UP" : "DOWN";
        if (!up) {
            log.warn("Health check DOWN: mysql={}, redis={}", mysqlOk, redisOk);
        }
        return Result.ok(Map.of(
                "status", status,
                "service", serviceName,
                "version", version,
                "timestamp", Instant.now().toString()));
    }

    private boolean checkMysql() {
        try {
            // 注：setQueryTimeout(2) 仅约束 SQL 执行时长；连接获取受 Hikari connection-timeout
            //（默认 30s）约束，池耗尽且 DB 同时不可用时代理最长可能阻塞约 30s，属已知局限。
            Integer one = jdbcTemplate.execute((ConnectionCallback<Integer>) con -> {
                try (PreparedStatement ps = con.prepareStatement("SELECT 1")) {
                    ps.setQueryTimeout(2); // ≤2s 快速失败，不挂起探活请求
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() && rs.getInt(1) == 1 ? 1 : 0;
                    }
                }
            });
            return one != null && one == 1;
        } catch (Exception e) {
            log.warn("MySQL health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            String pong = stringRedisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}
