package com.dating.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis key 构建器 —— 所有 user-service 的 key 统一在这里拼。
 * 前缀从 application-dev.yml 的 app.cache.key-prefix 注入（zhaoshihang）。
 *
 * key 格式为 {<prefix>:<domain>:<id>}，如zhaoshihang:user:profile:123
 * 见 1ARCHITECTURE.md §15.4。
 */
@Component
public class CacheKeyBuilder {

    @Value("${app.cache.key-prefix}")
    private String prefix;

    /** 用户档案缓存（Hash，TTL 30 min） */
    public String userProfile(Long userId) {
        return prefix + ":user:profile:" + userId;
    }

    /** 用户大档案缓存（含 interests，TTL 30 min） */
    public String userProfileBig(Long userId) {
        return prefix + ":user:profile:big:" + userId;
    }

    /** 用户兴趣缓存（Hash，TTL 30 min） */
    public String userInterest(Long userId) {
        return prefix + ":user:interest:" + userId;
    }

    /** 用户封禁状态缓存（Hash，TTL 5 min） */
    public String userBanStatus(Long userId) {
        return prefix + ":user:ban:status:" + userId;
    }

    /** 第三方封禁集合（Set，永久，运营维护） */
    public String userBanThirdPartySet() {
        return prefix + ":user:ban:thirdparty-set";
    }

    // ─── Redisson 分布式锁 ───

    public String lockRegisterPhone(String phoneE164) {
        return prefix + ":lock:user:register:phone:" + phoneE164;
    }

    public String lockRegisterThirdParty(int platform, String thirdPartyUserId) {
        return prefix + ":lock:user:register:thirdparty:" + platform + ":" + thirdPartyUserId;
    }

    public String lockRegisterDevice(int platform, String deviceId) {
        return prefix + ":lock:user:register:device:" + platform + ":" + deviceId;
    }
}
