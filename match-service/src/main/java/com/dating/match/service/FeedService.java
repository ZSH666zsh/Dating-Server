package com.dating.match.service;

import com.dating.match.client.UserServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.constant.TargetUserType;
import com.dating.zhaoshihang.proto.user.UserProfileProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Feed 推荐服务。
 * 对应 match-service-prd-tech.md §4.3 队列消费。
 *
 * <p>GetTodayFeed：LPOP 消费 Redis LIST，空了触发 D0 冷启动重建，二次过滤已 swipe 的卡片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final ColdStartService coldStartService;
    private final UserServiceClient userClient;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 获取今日推荐卡片。
     *
     * @param userId 当前用户
     * @param count  期望拉取数量（≤20）
     * @return 卡片列表
     */
    public List<CardResult> getTodayFeed(Long userId, int count) {
        // 先查用户性别（用于冷启动）
        Boolean isMale = userClient.isMale(userId);
        int gender = Boolean.TRUE.equals(isMale) ? 1 : 2;

        String feedKey = keyBuilder.feed(userId);
        String swipedKey = keyBuilder.swiped(userId);

        List<CardResult> result = new ArrayList<>();
        int need = Math.min(count, 20);

        while (result.size() < need) {
            // LPOP推荐队列 一批

            // 队列非空 → 取 count 张卡片
            List<String> batch = Optional.ofNullable(
                            redisTemplate.opsForList().leftPop(feedKey, need - result.size()))
                    .orElse(List.of());

            if (batch.isEmpty()) {
                // 队列空了，实时重建队列，然后继续消费
                int pushed = coldStartService.buildAndPush(userId, gender);
                if (pushed == 0) break; // 重建失败，极端兜底
                continue;
            }

            // 二次过滤已 swipe，已划过 → 丢弃（防止喂到用户划过的卡）
            for (String cardStr : batch) {
                String[] parts = cardStr.split(":");
                if (parts.length != 2) continue;
                Long targetId = Long.parseLong(parts[0]);

                // 检查是否已划过
                Boolean swiped = redisTemplate.opsForSet().isMember(swipedKey, parts[0]);
                if (Boolean.TRUE.equals(swiped)) continue; // 已划过，丢弃

                int targetType = Integer.parseInt(parts[1]);
                result.add(new CardResult(targetId, targetType));
            }
        }

        if (result.isEmpty()) return result;

        // userClient.batchGetProfile 批量获取用户资料，拼装昵称/年龄/照片 → 返回CardResult 列表
        List<Long> userIds = result.stream().map(CardResult::getTargetUserId).toList();
        Map<Long, UserProfileProto> profiles = userClient.batchGetProfile(userIds);

        for (CardResult card : result) {
            UserProfileProto profile = profiles.get(card.getTargetUserId());
            if (profile != null) {
                card.setNickname(profile.getNickname());
                card.setAge(profile.getAge());
                // photo_keys 从 custom_avatar JSON 提取（简化：暂用空列表）
                card.setPhotoKeys(List.of());
                card.setBio("");
                card.setDistanceKm(-1.0);
            }
        }

        return result;
    }

    /**
     * Feed 卡片结果。
     */
    public static class CardResult {
        private final Long targetUserId;
        private final int targetUserType;
        private String nickname;
        private int age;
        private List<String> photoKeys;
        private String bio;
        private double distanceKm;

        public CardResult(Long targetUserId, int targetUserType) {
            this.targetUserId = targetUserId;
            this.targetUserType = targetUserType;
        }

        // getters
        public Long getTargetUserId() { return targetUserId; }
        public int getTargetUserType() { return targetUserType; }
        public String getNickname() { return nickname; }
        public int getAge() { return age; }
        public List<String> getPhotoKeys() { return photoKeys; }
        public String getBio() { return bio; }
        public double getDistanceKm() { return distanceKm; }

        public void setNickname(String nickname) { this.nickname = nickname; }
        public void setAge(int age) { this.age = age; }
        public void setPhotoKeys(List<String> photoKeys) { this.photoKeys = photoKeys; }
        public void setBio(String bio) { this.bio = bio; }
        public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    }
}
