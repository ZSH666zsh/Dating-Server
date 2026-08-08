package com.dating.match.manager;

import com.dating.match.config.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 配额管理器（Redis HASH 存储）。
 * 对应 match-service-prd-tech.md §7.3 Redis Key 设计。
 *
 * <p>配额数据只存 Redis，不持久化到 PG。最坏情况丢一天配额（用户至多多刷几张卡）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaManager {

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 获取当日配额 key */
    private String quotaKey(Long userId) {
        return keyBuilder.quota(userId, LocalDate.now().format(DAY_FMT));
    }

    /**
     * 原子累加配额，返回累加后的值。
     * @return 累加后的新值
     */
    public long incr(String quotaKey, String field, long delta) {
        Long val = redisTemplate.opsForHash().increment(quotaKey, field, delta);
        // 36h TTL
        redisTemplate.expire(quotaKey, java.time.Duration.ofHours(36));
        return val != null ? val : 0;
    }

    /** 累加右划次数 */
    public long incrRightSwipe(Long userId) {
        return incr(quotaKey(userId), "right_swipe", 1);
    }

    /** 累加卡片消费 */
    public long incrCards(Long userId) {
        return incr(quotaKey(userId), "cards", 1);
    }

    /** 累加 SuperHi */
    public long incrSuperHi(Long userId) {
        return incr(quotaKey(userId), "super_hi", 1);
    }

    /** 回滚配额（超额时扣回） */
    public void rollback(String quotaKey, String field, long delta) {
        redisTemplate.opsForHash().increment(quotaKey, field, -delta);
    }

    /** 读取全部配额 */
    public Map<Object, Object> getAll(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(quotaKey(userId));
        if (entries.isEmpty()) {
            return Map.of("right_swipe", "0", "cards", "0", "super_hi", "0");
        }
        return entries;
    }
}
