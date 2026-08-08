package com.dating.gateway.controller;

import com.dating.gateway.security.JwtIssuer;
import com.dating.gateway.service.AuthService;
import com.dating.gateway.service.SmsService;
import com.dating.gateway.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 REST 接口。
 * 对应 mobile-gateway-design.md §5.5 接口清单。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;
    private final JwtIssuer jwtIssuer;

    /**
     * 发送短信验证码
     * POST /api/v1/auth/send-sms-code
     */
    @PostMapping("/send-sms-code")
    public Result<Map<String, Object>> sendSmsCode(@RequestBody SendSmsReq req) {
        String code = smsService.issueCode(req.phone());
        return Result.ok(Map.of("cooldownSec", 60, "mockCode", code));
    }

    /**
     * 手机号登录
     * POST /api/v1/auth/login-phone
     */
    @PostMapping("/login-phone")
    public Result<AuthService.LoginResult> loginByPhone(@RequestBody LoginPhoneReq req) {
        var result = authService.loginByPhone(req.phone(), req.smsCode(), req.deviceId(), req.platform());
        return Result.ok(result);
    }

    /**
     * 设备匿名登录
     * POST /api/v1/auth/login-device
     */
    @PostMapping("/login-device")
    public Result<AuthService.LoginResult> loginByDevice(@RequestBody LoginDeviceReq req) {
        var result = authService.loginByDevice(req.deviceId(), req.platform());
        return Result.ok(result);
    }

    /**
     * 刷新 token
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public Result<AuthService.LoginResult> refresh(@RequestBody RefreshReq req) {
        var result = authService.refresh(req.refreshToken(), req.deviceId());
        return Result.ok(result);
    }

    /**
     * 登出
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String deviceId = (String) request.getAttribute("deviceId");
        String authHeader = request.getHeader("Authorization");
        if (userId != null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            var parsed = jwtIssuer.parseAccessToken(token);
            if (parsed != null) {
                authService.logout(userId, deviceId, parsed.jti(), parsed.expiresAt());
            }
        }
        return Result.ok();
    }

    // ─── 请求体 ───

    public record SendSmsReq(String phone) {}
    public record LoginPhoneReq(String phone, String smsCode, String deviceId, int platform) {}
    public record LoginDeviceReq(String deviceId, int platform) {}
    public record RefreshReq(String refreshToken, String deviceId) {}
}
