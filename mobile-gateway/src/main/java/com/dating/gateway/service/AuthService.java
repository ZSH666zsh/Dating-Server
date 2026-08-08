package com.dating.gateway.service;

import com.dating.gateway.client.UserIdentityClient;
import com.dating.gateway.constant.ErrorCode;
import com.dating.gateway.entity.AuthDevice;
import com.dating.gateway.entity.AuthRefreshToken;
import com.dating.gateway.exception.BizException;
import com.dating.gateway.mapper.AuthDeviceMapper;
import com.dating.gateway.mapper.AuthRefreshTokenMapper;
import com.dating.gateway.security.JwtIssuer;
import com.dating.gateway.security.TokenBlacklistManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * 认证服务：登录 / 刷新 / 登出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserIdentityClient identityClient;
    private final AuthDeviceMapper authDeviceMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final JwtIssuer jwtIssuer;
    private final TokenBlacklistManager blacklistManager;
    private final SmsService smsService;

    /**
     * 手机号登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResult loginByPhone(String phoneE164, String smsCode, String deviceId, int platform) {
        // 校验验证码
        if (!smsService.verifyCode(phoneE164, smsCode)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "验证码错误或已过期");
        }

        // 调 user-service 注册/登录
        var identityResp = identityClient.resolveOrCreateByPhone(phoneE164);
        if (identityResp.getBase().getCode() != 0) {
            throw new BizException(identityResp.getBase().getCode(), identityResp.getBase().getMessage());
        }

        Long userId = identityResp.getUserId();

        // 封禁检查
        var banResp = identityClient.checkBan(userId);
        if (banResp.getBanned()) {
            throw new BizException(ErrorCode.USER_BANNED, "账号已被封禁");
        }

        // upsert 设备
        upsertDevice(userId, deviceId, platform);

        // 签发 token
        return issueTokenPair(userId, deviceId, identityResp.getNewlyCreated());
    }

    /**
     * 设备 ID 快速登录（匿名）。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResult loginByDevice(String deviceId, int platform) {
        var identityResp = identityClient.resolveOrCreateByDevice(deviceId, platform);
        if (identityResp.getBase().getCode() != 0) {
            throw new BizException(identityResp.getBase().getCode(), identityResp.getBase().getMessage());
        }

        Long userId = identityResp.getUserId();

        // 封禁检查（可选：匿名用户不强制）
        try {
            var banResp = identityClient.checkBan(userId);
            if (banResp.getBanned()) {
                throw new BizException(ErrorCode.USER_BANNED, "账号已被封禁");
            }
        } catch (BizException e) {
            if (e.getCode() != ErrorCode.USER_BANNED) throw e;
            throw e;
        }

        upsertDevice(userId, deviceId, platform);
        return issueTokenPair(userId, deviceId, identityResp.getNewlyCreated());
    }

    /**
     * 刷新 token。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResult refresh(String refreshTokenRaw, String deviceId) {
        String hash = sha256(refreshTokenRaw);

        // 查 PG
        var existing = refreshTokenMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthRefreshToken>()
                        .eq(AuthRefreshToken::getTokenHash, hash));

        if (existing == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "Refresh token 不存在");
        }

        // 已使用 → 重放检测
        if (existing.getUsedAt() != null) {
            // 撤销该用户该设备全部 refresh token
            revokeAllByUserDevice(existing.getUserId(), existing.getDeviceId());
            throw new BizException(ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token 已被使用，所有 token 已撤销");
        }

        // 过期检查
        if (existing.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "Refresh token 已过期");
        }

        // 设备匹配检查
        if (!deviceId.equals(existing.getDeviceId())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_DEVICE_MISMATCH, "设备不匹配");
        }

        // 标记旧 token 已使用
        existing.setUsedAt(OffsetDateTime.now());
        refreshTokenMapper.updateById(existing);

        // 签发新 token 对
        Long userId = existing.getUserId();
        var result = issueTokenPair(userId, deviceId, false);

        // 链表标记
        var newToken = refreshTokenMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthRefreshToken>()
                        .eq(AuthRefreshToken::getTokenHash, sha256(result.refreshToken()))
                        .eq(AuthRefreshToken::getUserId, userId));
        if (newToken != null) {
            existing.setRotatedToId(newToken.getId());
            refreshTokenMapper.updateById(existing);
        }

        return result;
    }

    /**
     * 登出。
     */
    public void logout(Long userId, String deviceId, String jti, java.time.Instant expiresAt) {
        // 加入黑名单
        blacklistManager.blacklist(jti, expiresAt);
        // 撤销 refresh token
        revokeAllByUserDevice(userId, deviceId);
        log.info("User logout: userId={} deviceId={}", userId, deviceId);
    }

    // ─── 内部方法 ───

    private void upsertDevice(Long userId, String deviceId, int platform) {
        try {
            var existing = authDeviceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthDevice>()
                            .eq(AuthDevice::getUserId, userId)
                            .eq(AuthDevice::getDeviceId, deviceId));
            if (existing != null) {
                existing.setPlatform(platform);
                authDeviceMapper.updateById(existing);
            } else {
                AuthDevice device = new AuthDevice();
                device.setUserId(userId);
                device.setDeviceId(deviceId);
                device.setPlatform(platform);
                authDeviceMapper.insert(device);
            }
        } catch (Exception e) {
            log.warn("upsertDevice failed (non-fatal): userId={} deviceId={}", userId, deviceId, e);
        }
    }

    private LoginResult issueTokenPair(Long userId, String deviceId, boolean newlyCreated) {
        String accessToken = jwtIssuer.issueAccessToken(userId, deviceId);
        String refreshToken = jwtIssuer.issueRefreshToken();

        // 存 refresh token hash
        AuthRefreshToken entity = new AuthRefreshToken();
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(7));
        refreshTokenMapper.insert(entity);

        return new LoginResult(userId, accessToken, refreshToken, newlyCreated,
                15 * 60 * 1000L,     // access expires in ms
                7 * 24 * 60 * 60 * 1000L); // refresh expires in ms
    }

    private void revokeAllByUserDevice(Long userId, String deviceId) {
        refreshTokenMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthRefreshToken>()
                        .eq(AuthRefreshToken::getUserId, userId)
                        .eq(AuthRefreshToken::getDeviceId, deviceId));
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ─── 结果 DTO ───

    public record LoginResult(Long userId, String accessToken, String refreshToken,
                              boolean pending, long accessExpiresAtMs, long refreshExpiresAtMs) {}
}
