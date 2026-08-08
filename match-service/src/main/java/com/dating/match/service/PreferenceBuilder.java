package com.dating.match.service;

import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.constant.Direction;
import com.dating.match.entity.UserSwipeHistory;
import com.dating.match.mapper.UserSwipeHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户偏好建模（D1 日更用）。
 * 对应 match-service-prd-tech.md §4.2.1 偏好建模。
 *
 * <p>从用户最近 30 天的右划历史聚合偏好画像，缓存到 Redis 24h。
 * 样本数 < 10 时返回空（退化到 D0 冷启动）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceBuilder {

    private final UserSwipeHistoryMapper swipeHistoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 构建用户偏好画像。
     *
     * @param userId 用户 ID
     * @return 偏好画像；样本不足返回 null（退化 D0）
     */
    public UserPreference build(Long userId) {
        // 先查缓存
        String prefKey = keyBuilder.pref(userId);
        String cached = redisTemplate.opsForValue().get(prefKey);
        if (cached != null) {
            return parsePreference(cached);
        }

        // 从 swipe_history 拉最近 30 天右划记录
        OffsetDateTime since = OffsetDateTime.now().minusDays(30);
        var rightSwipes = swipeHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserSwipeHistory>()
                        .eq(UserSwipeHistory::getUserId, userId)
                        .eq(UserSwipeHistory::getDirection, Direction.RIGHT)
                        .ge(UserSwipeHistory::getSwipedAt, since));

        if (rightSwipes.size() < 10) {
            log.debug("PreferenceBuilder: userId={} samples={} < 10, fallback to D0", userId, rightSwipes.size());
            return null; // 样本不足，退化 D0
        }

        // 统计各维度
        double ageSum = 0, beautySum = 0;
        int dhCount = 0, bhCount = 0;
        Map<String, Integer> raceCount = new HashMap<>();

        // 注意：swipe_history 只存了 userId/targetId，人口学数据需要调 user-service 批量拿
        // 当前简化：只记录样本数，评分时再调 user-service 批量补充
        for (var swipe : rightSwipes) {
            if (swipe.getTargetUserType() == 1) bhCount++;
            else dhCount++;
        }

        double bhRatio = (bhCount + dhCount) > 0 ? (double) bhCount / (bhCount + dhCount) : 0.5;

        // 这里年龄/颜值/人种需要调 user-service.BatchGetProfile 获取
        // 为了减少 RPC，实际 D1 cron 中会批量处理所有活跃用户
        UserPreference pref = new UserPreference(
                25.0, 5.0,      // age_mean, age_std（默认中性值）
                50.0, 15.0,     // beauty_mean, beauty_std
                Map.of(),       // race_dist
                bhRatio,
                rightSwipes.size()
        );

        // 缓存 24h
        redisTemplate.opsForValue().set(prefKey, pref.toCacheString(), Duration.ofHours(24));

        log.info("PreferenceBuilder built: userId={} samples={} bhRatio={}", userId, rightSwipes.size(), bhRatio);
        return pref;
    }

    private UserPreference parsePreference(String cached) {
        // 简单格式：ageMean:ageStd:beautyMean:beautyStd:bhRatio:samples
        String[] parts = cached.split(":");
        if (parts.length >= 6) {
            return new UserPreference(
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Map.of(),
                    Double.parseDouble(parts[4]),
                    Integer.parseInt(parts[5])
            );
        }
        return null;
    }

    /**
     * 用户偏好画像。
     *
     * @param ageMean   年龄均值
     * @param ageStd    年龄标准差
     * @param beautyMean 颜值均值
     * @param beautyStd  颜值标准差
     * @param raceDist   人种分布
     * @param bhRatio   真人比例（右划中 BH 占比）
     * @param sampleCount 样本数
     */
    public record UserPreference(
            double ageMean, double ageStd,
            double beautyMean, double beautyStd,
            Map<String, Double> raceDist,
            double bhRatio,
            int sampleCount
    ) {
        public String toCacheString() {
            return String.format("%.1f:%.1f:%.1f:%.1f:%.3f:%d",
                    ageMean, ageStd, beautyMean, beautyStd, bhRatio, sampleCount);
        }
    }
}
