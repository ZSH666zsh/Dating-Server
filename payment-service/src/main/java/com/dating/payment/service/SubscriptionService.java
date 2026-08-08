package com.dating.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.payment.entity.UserSubscription;
import com.dating.payment.mapper.UserSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 订阅服务。
 * 对应 payment-service-design.md §7。
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserSubscriptionMapper subscriptionMapper;

    /**
     * 获取订阅信息。
     */
    public SubscriptionInfo getSubscription(Long userId) {
        var sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));
        if (sub == null || sub.getExpiresAt() == null
                || sub.getExpiresAt().isBefore(OffsetDateTime.now())
                || sub.getTier() <= 1) {
            return new SubscriptionInfo("FREE", null, false);
        }
        return new SubscriptionInfo(
                tierToString(sub.getTier()),
                sub.getExpiresAt(),
                true
        );
    }

    /**
     * 激活/续期订阅。
     */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionInfo activateSubscription(Long userId, String tierStr, int durationDays, String source) {
        int newTier = parseTier(tierStr);
        if (newTier <= 1) return getSubscription(userId); // FREE 不处理

        var existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));

        if (existing != null) {
            // 只升不降 + 时长顺延
            OffsetDateTime base = existing.getExpiresAt();
            if (base == null || base.isBefore(OffsetDateTime.now())) {
                base = OffsetDateTime.now();
            }
            existing.setTier(Math.max(existing.getTier(), newTier));  // 只升不降
            existing.setExpiresAt(base.plusDays(durationDays));
            existing.setSource(source);
            subscriptionMapper.updateById(existing);
        } else {
            existing = new UserSubscription();
            existing.setUserId(userId);
            existing.setTier(newTier);
            existing.setExpiresAt(OffsetDateTime.now().plusDays(durationDays));
            existing.setSource(source);
            subscriptionMapper.insert(existing);
        }

        return new SubscriptionInfo(tierToString(existing.getTier()), existing.getExpiresAt(), true);
    }

    private String tierToString(int tier) {
        return switch (tier) {
            case 2 -> "WEEKLY";
            case 3 -> "MONTHLY";
            case 4 -> "YEARLY";
            default -> "FREE";
        };
    }

    private int parseTier(String tier) {
        return switch (tier.toUpperCase()) {
            case "WEEKLY" -> 2;
            case "MONTHLY" -> 3;
            case "YEARLY" -> 4;
            default -> 1;
        };
    }

    public record SubscriptionInfo(String tier, OffsetDateTime expiresAt, boolean isActive) {}
}
