package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.entity.UserSwipeHistory;
import com.dating.match.mapper.UserSwipeHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 划卡历史管理器。
 */
@Component
@RequiredArgsConstructor
public class SwipeHistoryManager {

    private final UserSwipeHistoryMapper swipeHistoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 检查是否已划过某个用户。
     */
    public boolean exists(Long userId, Long targetUserId) {
        Long count = swipeHistoryMapper.selectCount(
                new LambdaQueryWrapper<UserSwipeHistory>()
                        .eq(UserSwipeHistory::getUserId, userId)
                        .eq(UserSwipeHistory::getTargetUserId, targetUserId));
        return count != null && count > 0;
    }

    /**
     * 插入划卡记录。
     */
    public void insert(UserSwipeHistory history) {
        swipeHistoryMapper.insert(history);
        // 同步写入 swiped SET 缓存
        String swipedKey = keyBuilder.swiped(history.getUserId());
        redisTemplate.opsForSet().add(swipedKey, String.valueOf(history.getTargetUserId()));
    }

    /**
     * 获取用户已 swipe 过的所有 target ID。
     */
    public List<Long> getSwipedTargetIds(Long userId) {
        return swipeHistoryMapper.selectList(
                        new LambdaQueryWrapper<UserSwipeHistory>()
                                .select(UserSwipeHistory::getTargetUserId)
                                .eq(UserSwipeHistory::getUserId, userId))
                .stream()
                .map(UserSwipeHistory::getTargetUserId)
                .toList();
    }

    /**
     * 批量检查是否已划过（基于 Redis SET）。
     */
    public List<Boolean> batchCheckSwiped(Long userId, List<Long> targetIds) {
        String swipedKey = keyBuilder.swiped(userId);
        return targetIds.stream()
                .map(id -> Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(swipedKey, String.valueOf(id))))
                .toList();
    }
}
