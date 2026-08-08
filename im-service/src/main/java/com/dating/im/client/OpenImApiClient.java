package com.dating.im.client;

import com.dating.im.config.ImConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenIM REST API 客户端。
 * 对应 dev-onboarding.md §7 OpenIM。
 *
 * <h3>使用方式</h3>
 * <p>通过 OpenIM Admin API（方式 B）在服务端管理用户和发消息。
 * 需要管理员提供 admin secret（从 Nacos 配置 openim.admin-secret 注入）。
 *
 * <h3>API 地址</h3>
 * <p>OpenIM REST API: {@code https://nexus-mind.chatvibe.me/api}
 *
 * <h3>当前状态</h3>
 * <p>OpenIM 共享服务已部署但当前网络不可达。
 * 代码已完整实现，网络通即可运行。
 * 所有调用均有 try-catch 兜底，失败不阻塞主流程。
 */
@Slf4j
@Component
public class OpenImApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openim.api-base-url:https://nexus-mind.chatvibe.me/api}")
    private String apiBaseUrl;

    /** admin secret 由老师/管理员提供，从 Nacos 配置 openim.admin-secret 注入 */
    @Value("${openim.admin-secret:}")
    private String adminSecret;

    /** 缓存的 admin token */
    private String adminToken;
    private long adminTokenExpireAtMs = 0;

    public OpenImApiClient(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (adminSecret.isEmpty()) {
            log.warn("OpenIM admin-secret not configured. Set Nacos config 'openim.admin-secret'. "
                    + "IM user registration and notifications will be degraded.");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  用户管理
    // ════════════════════════════════════════════════════════════════

    /**
     * 在 OpenIM 中注册用户（幂等）。
     * 对应 OpenIM API: POST /api/user/user_register
     *
     * @param userId   你的业务 userId
     * @param nickname 昵称
     * @param photoUrl 头像 URL（可选，传空字符串则 OpenIM 使用默认头像）
     * @return true=注册成功（或已存在），false=失败
     */
    public boolean registerUser(long userId, String nickname, String photoUrl) {
        try {
            String token = getAdminToken();
            if (token == null) {
                log.warn("OpenIM registerUser skipped: no admin token");
                return false;
            }

            String url = apiBaseUrl + "/user/user_register";
            Map<String, Object> body = new HashMap<>();
            body.put("secret", adminSecret);
            body.put("users", List.of(Map.of(
                    "userID", String.valueOf(userId),
                    "nickname", nickname != null ? nickname : "User_" + userId,
                    "faceURL", photoUrl != null ? photoUrl : "",
                    "ex", ""
            )));

            var resp = postJson(url, token, body);
            if (resp != null) {
                int errCode = resp.path("errCode").asInt(999);
                if (errCode == 0) {
                    log.info("OpenIM register success: userId={}", userId);
                    return true;
                }
                // errCode=100(已存在)也算成功
                if (errCode == 100) {
                    log.debug("OpenIM user already exists: userId={}", userId);
                    return true;
                }
                log.warn("OpenIM register failed: userId={} errCode={} msg={}",
                        userId, errCode, resp.path("errMsg").asText(""));
            }
        } catch (Exception e) {
            log.warn("OpenIM registerUser error (non-fatal): userId={}", userId, e);
        }
        return false;
    }

    /**
     * 获取用户的 IM token。
     * 对应 OpenIM API: POST /api/auth/get_user_token
     *
     * @param userId 业务 userId
     * @return IM token（App 端用这个登录 OpenIM SDK），null 表示失败
     */
    public String getUserToken(long userId) {
        try {
            String token = getAdminToken();
            if (token == null) return null;

            String url = apiBaseUrl + "/auth/get_user_token";
            Map<String, Object> body = new HashMap<>();
            body.put("secret", adminSecret);
            body.put("platform", 5); // iOS=1 Android=2 Windows=4 Web=5 ...
            body.put("userID", String.valueOf(userId));

            var resp = postJson(url, token, body);
            if (resp != null && resp.path("errCode").asInt(999) == 0) {
                String imToken = resp.path("data").path("token").asText("");
                log.debug("OpenIM getToken success: userId={}", userId);
                return imToken;
            }
        } catch (Exception e) {
            log.warn("OpenIM getUserToken error (non-fatal): userId={}", userId, e);
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  消息发送
    // ════════════════════════════════════════════════════════════════

    /**
     * 发送业务通知消息（匹配成功、系统消息等）。
     * 对应 OpenIM API: POST /api/msg/send_business_notification
     *
     * @param fromUserId 发送方 userId（系统通知一般用 0 或管理账号）
     * @param toUserId   接收方 userId
     * @param key        通知类型（match_success / system_msg / typing ...）
     * @param payloadJson 通知内容 JSON
     * @return true=发送成功，false=失败
     */
    public boolean sendBusinessNotification(long fromUserId, long toUserId,
                                             String key, String payloadJson) {
        try {
            String token = getAdminToken();
            if (token == null) return false;

            String url = apiBaseUrl + "/msg/send_business_notification";
            Map<String, Object> body = new HashMap<>();
            body.put("fromUserID", String.valueOf(fromUserId));
            body.put("recvUserID", String.valueOf(toUserId));
            body.put("key", key);
            body.put("data", payloadJson);

            var resp = postJson(url, token, body);
            if (resp != null && resp.path("errCode").asInt(999) == 0) {
                log.debug("OpenIM biz notification sent: key={} to={}", key, toUserId);
                return true;
            }
        } catch (Exception e) {
            log.warn("OpenIM sendBusinessNotification error (non-fatal): key={} to={}", key, toUserId, e);
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  内部方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 获取 admin token（带缓存）。
     * 对应 OpenIM API: POST /api/auth/get_admin_token
     */
    private String getAdminToken() {
        // 缓存未过期
        if (adminToken != null && System.currentTimeMillis() < adminTokenExpireAtMs) {
            return adminToken;
        }

        if (adminSecret == null || adminSecret.isEmpty()) {
            log.warn("OpenIM admin-secret not configured");
            return null;
        }

        try {
            String url = apiBaseUrl + "/auth/get_admin_token";
            Map<String, Object> body = new HashMap<>();
            body.put("secret", adminSecret);

            var resp = postJson(url, null, body);
            if (resp != null && resp.path("errCode").asInt(999) == 0) {
                adminToken = resp.path("data").path("token").asText("");
                int expireSec = resp.path("data").path("expireTimeSeconds").asInt(3600);
                adminTokenExpireAtMs = System.currentTimeMillis() + (expireSec - 60) * 1000L;
                log.info("OpenIM admin token obtained, expires in {}s", expireSec);
                return adminToken;
            }
        } catch (Exception e) {
            log.warn("OpenIM getAdminToken failed", e);
        }
        return null;
    }

    /** POST JSON 到 OpenIM API */
    private JsonNode postJson(String url, String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("operationID", "im-" + System.currentTimeMillis());
        if (token != null && !token.isEmpty()) {
            headers.set("token", token);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            try {
                return objectMapper.readTree(resp.getBody());
            } catch (Exception e) {
                log.warn("OpenIM API response parse failed: url={} body={}", url, resp.getBody(), e);
                return null;
            }
        }
        log.warn("OpenIM API returned status={} url={}", resp.getStatusCode(), url);
        return null;
    }
}
