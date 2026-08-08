package com.dating.match.service;

import com.dating.match.client.UserServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.constant.TargetUserType;
import com.dating.zhaoshihang.proto.user.UserProfileProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * D0 冷启动实时召回服务。
 * 对应 match-service-prd-tech.md §4.1 D0 冷启动队列。
 *
 * <p>当用户 Feed 队列为空时触发，实时双池召回（DH + BH）并按比例 merge 后 RPUSH 到 Redis LIST。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColdStartService {

    private final UserServiceClient userClient;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /** BH 基准比例（Nacos 可调） */
    private double bhRatio = 0.20;

    /**
     * 构建并推送冷启动队列。
     * @param userId 当前用户
     * @param gender 用户性别（1=男 2=女）
     * @return 推送的卡片数
     */
    public int buildAndPush(Long userId, int gender) {
        int targetGender = gender == 1 ? 2 : 1; // 异性优先

        // 获取已排除列表（已经划过的 userIds）
        String swipedKey = keyBuilder.swiped(userId);
        Set<String> excludedSet = redisTemplate.opsForSet().members(swipedKey);
        List<Long> excludeIds = excludedSet.stream()
                .map(Long::parseLong)
                .toList();

        // 1) 从 user-service 拉取 DH 池 召回（渐进扩范围，目标 240张）
        List<UserProfileProto> dhPool = recallDhPool(targetGender, excludeIds, 240);
        // 2) 从 user-service 拉取 BH 池 召回（严格单层，目标 240张）
        List<UserProfileProto> bhPool = recallBhPool(userId, targetGender, excludeIds, 240);

        log.debug("ColdStart: userId={} DH={} BH={}", userId, dhPool.size(), bhPool.size());

        // 3) 按比例 bh_ratio（默认 0.2：0.8）merge
        List<String> feed = mergePool(bhPool, dhPool);

        // 4) RPUSH 到 Redis LIST（不带 DEL，允许累积），设置 TTL 7 天
        String feedKey = keyBuilder.feed(userId);
        feed.forEach(card -> redisTemplate.opsForList().rightPush(feedKey, card));
        redisTemplate.expire(feedKey, 7, TimeUnit.DAYS);

        log.info("ColdStart buildAndPush: userId={} pushed={}", userId, feed.size());
        return feed.size();
    }

    /**
     * DH 池召回（渐进扩范围 L0~L3）。
     */
    private List<UserProfileProto> recallDhPool(int targetGender, List<Long> excludeIds, int target) {
        Set<Long> seen = new HashSet<>();
        List<UserProfileProto> result = new ArrayList<>();

        // L0: 最严
        result.addAll(fetchDh(targetGender, 0, 100, 0, 100, List.of(), excludeIds, target));
        seen.addAll(result.stream().map(UserProfileProto::getUserId).toList());
        if (result.size() >= target) return result.subList(0, target);

        // L1: 放开人种
        // (已是最宽范围，直接返回)
        return result;
    }

    private List<UserProfileProto> fetchDh(int gender, int ageMin, int ageMax,
                                            int beautyMin, int beautyMax,
                                            List<String> races, List<Long> excludeIds, int limit) {
        return userClient.listDhCandidates(gender, ageMin, ageMax, beautyMin, beautyMax,
                races, excludeIds, limit);
    }

    /**
     * BH 池召回（严格单层）。
     */
    private List<UserProfileProto> recallBhPool(Long callerUserId, int targetGender,
                                                 List<Long> excludeIds, int target) {
        return userClient.nearbyUsers(callerUserId, targetGender, 7, excludeIds, target);
    }

    /**
     * 按比例 merge BH + DH。
     */
    private List<String> mergePool(List<UserProfileProto> bhPool, List<UserProfileProto> dhPool) {
        int targetBh = (int) Math.round(240 * bhRatio);
        int actualBh = Math.min(targetBh, bhPool.size());
        int actualDh = Math.min(240 - actualBh, dhPool.size());

        // 交错
        List<String> result = new ArrayList<>(240);
        int bhIdx = 0, dhIdx = 0;
        int interval = actualBh > 0 ? Math.max(1, (int) Math.round(1.0 / bhRatio)) : Integer.MAX_VALUE;
        int count = 0;
        while (bhIdx < actualBh || dhIdx < actualDh) {
            if (count % interval == 0 && bhIdx < actualBh) {
                result.add(formatCard(bhPool.get(bhIdx++), TargetUserType.BH));
            } else if (dhIdx < actualDh) {
                result.add(formatCard(dhPool.get(dhIdx++), TargetUserType.DH));
            } else if (bhIdx < actualBh) {
                result.add(formatCard(bhPool.get(bhIdx++), TargetUserType.BH));
            } else {
                break;
            }
            count++;
        }
        return result;
    }

    private String formatCard(UserProfileProto profile, int userType) {
        return profile.getUserId() + ":" + userType;
    }
}
