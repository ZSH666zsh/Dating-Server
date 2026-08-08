package com.dating.user.service;

import com.dating.user.config.CacheKeyBuilder;
import com.dating.user.config.SnowflakeIdGenerator;
import com.dating.user.constant.ErrorCode;
import com.dating.user.constant.Gender;
import com.dating.user.entity.*;
import com.dating.user.exception.BizException;
import com.dating.user.manager.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 用户身份服务 —— 注册 + 登录 + 封禁检查。
 *
 * <h3>三种登录方式</h3>
 * ① {@link #resolveOrCreateByPhone} — 手机号验证码登录
 * ② {@link #resolveOrCreateByThirdParty} — Google/Apple/Facebook 第三方登录
 * ③ {@link #resolveOrCreateByDevice} — 匿名设备登录
 *
 * <h3>防重复注册</h3>
 * 用 Redisson 分布式锁防并发重复。
 * 锁 key 格式：{@code lock:user:register:{type}:{id}}，见 1ARCHITECTURE.md §15.4。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityService {

    private final UserInfoManager userInfoManager;
    private final UserLoginManager userLoginManager;
    private final UserThirdPartyManager userThirdPartyManager;
    private final UserDeviceManager userDeviceManager;
    private final SnowflakeIdGenerator.Snowflake snowflake;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;

    /** 用户注册结果 */
    public record RegisterResult(Long userId, boolean pending, boolean newlyCreated) {}

    // ──────────────────────────────────────────────
    //  手机号登录
    // ──────────────────────────────────────────────

    /**
     * 手机号登录：已存在 → 返回；不存在 → 创建占位用户 + 绑定手机号。
     * 用分布式锁防并发重复注册。
     */
    public RegisterResult resolveOrCreateByPhone(String phoneE164) {
        String lockKey = cacheKeyBuilder.lockRegisterPhone(phoneE164);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 分布式锁？ 防止两个线程同时收到同一手机号的注册请求，都查不到记录，都去 INSERT。
            // 尝试加锁，等锁最多 3 秒，持锁最多 30 秒
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后再试");
            }

            // 双重检查保证线程安全：是否已注册，查 user_login_phone 表是否存在
            UserLoginPhone existingBinding = userLoginManager.getByPhone(phoneE164);
            if (existingBinding != null) {
                UserInfo user = userInfoManager.getByUserId(existingBinding.getUserId());
                if (user != null) {
                    touchLastOpen(user);
                    return new RegisterResult(user.getUserId(), user.getPending(), false);
                }
            }

            // 新建用户，不存在 → 雪花 ID 生成 userId
            Long userId = snowflake.nextId();
            // 创建占位用户
            UserInfo placeholder = createPlaceholder(userId);
            userInfoManager.insert(placeholder);

            // 绑定手机号
            UserLoginPhone binding = new UserLoginPhone();
            binding.setUserId(userId);
            binding.setPhoneE164(phoneE164);
            binding.setAppName("vibe");
            // 绑定手机号
            userLoginManager.insert(binding);

            log.info("User created via phone: userId={} phone={}", userId, phoneE164);
            return new RegisterResult(userId, true, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙");
        } finally {
            // finally 解锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ──────────────────────────────────────────────
    //  第三方登录和设备登录 的流程完全相同，只是绑定的表不同
    // ──────────────────────────────────────────────

    /**
     * 第三方登录（Google/Apple/Facebook）。
     * platform: 1=Google 2=Facebook 3=Apple
     */
    public RegisterResult resolveOrCreateByThirdParty(String thirdPartyUserId, int platform) {
        String lockKey = cacheKeyBuilder.lockRegisterThirdParty(platform, thirdPartyUserId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后再试");
            }

            UserThirdPartyRegistration existing = userThirdPartyManager.getByThirdPartyId(thirdPartyUserId, platform);
            if (existing != null) {
                UserInfo user = userInfoManager.getByUserId(existing.getUserId());
                if (user != null) {
                    touchLastOpen(user);
                    return new RegisterResult(user.getUserId(), user.getPending(), false);
                }
            }

            Long userId = snowflake.nextId();
            userInfoManager.insert(createPlaceholder(userId));

            UserThirdPartyRegistration binding = new UserThirdPartyRegistration();
            binding.setUserId(userId);
            binding.setThirdPartyLoginUserId(thirdPartyUserId);
            binding.setPlatform(platform);
            userThirdPartyManager.insert(binding);

            log.info("User created via third-party: userId={} platform={}", userId, platform);
            return new RegisterResult(userId, true, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ──────────────────────────────────────────────
    //  设备匿名登录
    // ──────────────────────────────────────────────

    /**
     * 设备登录（匿名）。platform: 1=iOS 2=Android 3=Web
     */
    public RegisterResult resolveOrCreateByDevice(String deviceId, int platform) {
        String lockKey = cacheKeyBuilder.lockRegisterDevice(platform, deviceId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后再试");
            }

            UserDeviceRegistration existing = userDeviceManager.getByDeviceId(deviceId, platform);
            if (existing != null) {
                UserInfo user = userInfoManager.getByUserId(existing.getUserId());
                if (user != null) {
                    touchLastOpen(user);
                    return new RegisterResult(user.getUserId(), user.getPending(), false);
                }
            }

            Long userId = snowflake.nextId();
            userInfoManager.insert(createPlaceholder(userId));

            UserDeviceRegistration binding = new UserDeviceRegistration();
            binding.setUserId(userId);
            binding.setDeviceId(deviceId);
            binding.setPlatform(platform);
            userDeviceManager.insert(binding);

            log.info("User created via device: userId={} deviceId={}", userId, deviceId);
            return new RegisterResult(userId, true, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ──────────────────────────────────────────────
    //  封禁检查
    // ──────────────────────────────────────────────

    /**
     * 封禁检查。
     * 先查 Redis 5min 缓存 → miss 查 DB → 回写缓存。
     */
    public boolean checkBan(Long userId) {
        // 查缓存
        String cacheKey = cacheKeyBuilder.userBanStatus(userId);
        String cached = (String) redisTemplate.opsForHash().get(cacheKey, "status");
        if ("2".equals(cached) || "3".equals(cached)) {
            return true; // 已封禁
        }

        // 缓存 miss → 查 DB
        UserInfo user = userInfoManager.getByUserId(userId);
        if (user == null) return false;

        boolean banned = user.getRegulationStatus() != null
                && (user.getRegulationStatus() == 2 || user.getRegulationStatus() == 3);

        // 回写 Redis (TTL 5min)缓存 5 分钟
        redisTemplate.opsForHash().put(cacheKey, "status", String.valueOf(user.getRegulationStatus()));
        redisTemplate.expire(cacheKey, 5, TimeUnit.MINUTES);

        return banned;
    }

    // ─── 私有方法 ───

    /** 创建占位用户（pending=true，等待 onboarding） */
    private UserInfo createPlaceholder(Long userId) {
        UserInfo user = new UserInfo();
        user.setUserId(userId);
        user.setGender(Gender.UNKNOWN);
        user.setPending(true);
        user.setUserType(1); // BH
        user.setLastOpenAt(OffsetDateTime.now());
        return user;
    }

    /** 更新最后活跃时间 */
    private void touchLastOpen(UserInfo user) {
        user.setLastOpenAt(OffsetDateTime.now());
        userInfoManager.updateSelective(user);
    }
}
