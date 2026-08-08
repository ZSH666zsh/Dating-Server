package com.dating.match.service;

import com.dating.match.client.PaymentServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.exception.BizException;
import com.dating.match.manager.QuotaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.dating.match.constant.ErrorCode.*;

/**
 * 配额管理服务。
 * 对应 match-service-prd-tech.md §3.1 订阅档位 + 配额。
 *
 * <p>配额数据只存 Redis HASH，不做 PG 持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaManager quotaManager;
    private final PaymentServiceClient paymentClient;
    private final CacheKeyBuilder keyBuilder;

    /** 各档位配额定义 */
    private static final Map<String, QuotaConfig> TIER_QUOTA = Map.of(
            "FREE",    new QuotaConfig(5, 50, 0),
            "WEEKLY",  new QuotaConfig(10, 80, 0),
            "MONTHLY", new QuotaConfig(15, 120, 1),
            "YEARLY",  new QuotaConfig(15, 120, 1)
    );

    /**
     * 获取用户当日配额（含已用）。
     */
    public QuotaInfo getQuota(Long userId) {
        String tier = paymentClient.getSubscription(userId);
        QuotaConfig config = TIER_QUOTA.getOrDefault(tier, TIER_QUOTA.get("FREE"));
        Map<Object, Object> used = quotaManager.getAll(userId);

        int rightSwipeUsed = intVal(used.get("right_swipe"));
        int cardsUsed = intVal(used.get("cards"));
        int superHiUsed = intVal(used.get("super_hi"));

        return new QuotaInfo(
                config.rightSwipeLimit, rightSwipeUsed,
                config.cardLimit, cardsUsed,
                config.superHiLimit, superHiUsed,
                100, tier
        );
    }

    /**
     * 检查并扣减右划配额。
     * @return 扣减后的值
     */
    public long checkAndConsumeRightSwipe(Long userId) {
        QuotaInfo quota = getQuota(userId);
        if (quota.dailyRightSwipeUsed >= quota.dailyRightSwipeLimit) {
            throw new BizException(QUOTA_RIGHT_SWIPE_EXCEEDED, "今日右划次数已用完");
        }
        return quotaManager.incrRightSwipe(userId);
    }

    /**
     * 检查并扣减卡片配额。
     */
    public long checkAndConsumeCard(Long userId) {
        QuotaInfo quota = getQuota(userId);
        if (quota.dailyCardUsed >= quota.dailyCardLimit) {
            throw new BizException(QUOTA_CARDS_EXCEEDED, "今日可划卡片已用完");
        }
        return quotaManager.incrCards(userId);
    }

    /**
     * 检查 Super Hi 配额。
     */
    public SuperHiQuotaResult checkSuperHi(Long userId) {
        QuotaInfo quota = getQuota(userId);
        boolean hasFree = quota.dailySuperHiUsed < quota.dailySuperHiLimit;
        return new SuperHiQuotaResult(hasFree, hasFree ? 0 : 100);
    }

    /**
     * 消耗 Super Hi（订阅赠送优先）。
     */
    public void consumeSuperHi(Long userId, int coinCost) {
        if (coinCost > 0) {
            // 扣金币（调用 payment-service）
            boolean ok = paymentClient.consumeCoins(userId, coinCost, "SUPER_HI",
                    "superhi:" + userId + ":" + System.currentTimeMillis());
            if (!ok) {
                throw new BizException(INSUFFICIENT_COINS, "金币不足");
            }
        }
        quotaManager.incrSuperHi(userId);
        quotaManager.incrCards(userId);
        quotaManager.incrRightSwipe(userId);
    }

    private int intVal(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    /** 配额配置 */
    public record QuotaConfig(int rightSwipeLimit, int cardLimit, int superHiLimit) {}

    /** 配额快照 */
    public record QuotaInfo(
            int dailyRightSwipeLimit, int dailyRightSwipeUsed,
            int dailyCardLimit, int dailyCardUsed,
            int dailySuperHiLimit, int dailySuperHiUsed,
            int superHiCoinPrice, String subscriptionTier) {}

    /** Super Hi 配额检查结果 */
    public record SuperHiQuotaResult(boolean hasFreeSuperHi, int coinCost) {}
}
