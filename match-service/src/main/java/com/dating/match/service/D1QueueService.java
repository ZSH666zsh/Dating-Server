package com.dating.match.service;

import com.dating.match.client.UserServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.constant.TargetUserType;
import com.dating.match.mapper.UserSwipeHistoryMapper;
import com.dating.match.service.PreferenceBuilder.UserPreference;
import com.dating.zhaoshihang.proto.user.UserProfileProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * D1 日更队列生成服务。
 * 对应 match-service-prd-tech.md §4.2 D1 日更队列。
 *
 * <p>每日 UTC 07:00 触发，为昨天有划卡行为的用户生成个性化推荐队列。
 * 流程：偏好建模 → 两池召回 → 打分排序 → 按比例 merge → DEL + RPUSH 覆盖 Redis LIST。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class D1QueueService {

    private static final int QUEUE_SIZE = 240;
    private static final double DEFAULT_BH_RATIO = 0.40;

    private final PreferenceBuilder preferenceBuilder;
    private final UserServiceClient userClient;
    private final UserSwipeHistoryMapper swipeHistoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 为一个用户生成 D1 队列。
     *
     * @param userId 用户 ID
     * @param gender 用户性别（1=男 2=女），用于异性优先
     * @return 生成的卡片数
     */
    public int generateForUser(Long userId, int gender) {
        int targetGender = gender == 1 ? 2 : 1;

        // 1. 偏好建模，从近 30 天右划历史统计 bhRatio，样本 < 10 → 退化到 D0
        UserPreference pref = preferenceBuilder.build(userId);
        double bhRatio = DEFAULT_BH_RATIO;
        if (pref != null) {
            // L2 个性化偏移：(0.5 - dh_bh_ratio) × 0.40，夹 [-0.20, +0.20]
            double offset = (0.5 - pref.bhRatio()) * 0.40;
            offset = Math.max(-0.20, Math.min(0.20, offset));
            bhRatio = Math.max(0, Math.min(1, DEFAULT_BH_RATIO + offset));
        }

        // 2. 获取排除列表（已 swipe 的用户）
        var swipedHistories = swipeHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.dating.match.entity.UserSwipeHistory>()
                        .eq(com.dating.match.entity.UserSwipeHistory::getUserId, userId));
        List<Long> excludeIds = swipedHistories.stream()
                .map(com.dating.match.entity.UserSwipeHistory::getTargetUserId)
                .distinct()
                .toList();

        // 3. 召回两池
        List<UserProfileProto> dhPool = recallDhPool(targetGender, excludeIds, QUEUE_SIZE);
        List<UserProfileProto> bhPool = recallBhPool(userId, targetGender, excludeIds, QUEUE_SIZE);

        log.debug("D1 recall: userId={} DH={} BH={}", userId, dhPool.size(), bhPool.size());

        // 4. 打分排序（D1 用打分公式，不同于 D0 的字典序）
        var scoredBh = scoreAndSort(bhPool, pref, userId, true);
        var scoredDh = scoreAndSort(dhPool, pref, userId, false);

        // 5. 按 bh_ratio（默认 0.40 = 40% BH）merge
        int targetBh = (int) Math.round(QUEUE_SIZE * bhRatio);
        int actualBh = Math.min(targetBh, scoredBh.size());
        int actualDh = Math.min(QUEUE_SIZE - actualBh, scoredDh.size());

        // 6. DEL + RPUSH 覆盖队列
        List<String> feed = new ArrayList<>(QUEUE_SIZE);
        int bhIdx = 0, dhIdx = 0, interval = actualBh > 0 ? Math.max(1, (int) Math.round(1.0 / bhRatio)) : QUEUE_SIZE;
        int count = 0;
        while (bhIdx < actualBh || dhIdx < actualDh) {
            boolean insertBh = (count % interval == 0 && bhIdx < actualBh);
            if (insertBh) {
                feed.add(formatCard(scoredBh.get(bhIdx++), TargetUserType.BH));
            } else if (dhIdx < actualDh) {
                feed.add(formatCard(scoredDh.get(dhIdx++), TargetUserType.DH));
            } else if (bhIdx < actualBh) {
                feed.add(formatCard(scoredBh.get(bhIdx++), TargetUserType.BH));
            } else {
                break;
            }
            count++;
        }

        // 6. DEL + RPUSH 覆盖
        String feedKey = keyBuilder.feed(userId);
        redisTemplate.delete(feedKey);
        feed.forEach(card -> redisTemplate.opsForList().rightPush(feedKey, card));
        redisTemplate.expire(feedKey, 7, TimeUnit.DAYS);

        log.info("D1 queue generated: userId={} BH={} DH={} total={}",
                userId, actualBh, actualDh, feed.size());
        return feed.size();
    }

    /**
     * DH 池召回。
     */
    private List<UserProfileProto> recallDhPool(int targetGender, List<Long> excludeIds, int target) {
        return userClient.listDhCandidates(targetGender, 18, 100, 0, 100,
                List.of(), excludeIds, target);
    }

    /**
     * BH 池召回。
     */
    private List<UserProfileProto> recallBhPool(Long callerUserId, int targetGender,
                                                  List<Long> excludeIds, int target) {
        return userClient.nearbyUsers(callerUserId, targetGender, 7, excludeIds, target);
    }

    /**
     * 打分排序。
     * 对应 match-service-prd-tech.md §4.2.3 池内打分。
     */
    private List<UserProfileProto> scoreAndSort(List<UserProfileProto> candidates,
                                                  UserPreference pref, Long callerUserId, boolean isBh) {
        if (candidates.isEmpty()) return List.of();

        // 如果没有偏好或样本不足，按颜值简单排序
        if (pref == null || pref.sampleCount() < 10) {
            return candidates.stream()
                    .sorted((a, b) -> Integer.compare(b.getBeautyScore(), a.getBeautyScore()))
                    .toList();
        }

        // 计算每项得分
        record Scored(UserProfileProto profile, double score) {}
        List<Scored> scored = new ArrayList<>();

        for (var c : candidates) {
            // preference_similarity（简化：用颜值差的负指数）
            double beautyDiff = Math.abs(c.getBeautyScore() - pref.beautyMean()) / Math.max(pref.beautyStd(), 1);
            double prefSim = Math.exp(-beautyDiff);

            double beautyNorm = Math.min(1.0, c.getBeautyScore() / 100.0);
            double distanceDecay = 0.5; // DH 固定 0.5
            double activityScore = 0.5;

            double base = 0.45 * prefSim + 0.30 * beautyNorm + 0.15 * distanceDecay + 0.10 * activityScore;

            // bonus（仅 BH）
            double bonus = 0;
            if (isBh && c.getCreatedAt() > 0) {
                long ageHours = (System.currentTimeMillis() - c.getCreatedAt()) / 3600000;
                if (ageHours < 72) bonus += 0.20; // new_bh_bonus：注册 <= 3 天
            }

            scored.add(new Scored(c, base + bonus));
        }

        // 按 score 降序
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.stream().map(Scored::profile).limit(QUEUE_SIZE).toList();
    }

    private String formatCard(UserProfileProto profile, int userType) {
        return profile.getUserId() + ":" + userType;
    }
}
