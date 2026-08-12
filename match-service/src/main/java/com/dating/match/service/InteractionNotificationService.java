package com.dating.match.service;

import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.entity.LikeRecord;
import com.dating.match.entity.VisitRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 互动通知服务（写库与通知解耦）。
 *
 * <p>like/visit 落库是权威（PG），互动通知是辅助（Redis）：
 * <ul>
 *   <li><b>异步</b>：{@link #notifyBatch} 用 {@code @Async} 在写库事务提交后触发，
 *       不占调度器关键路径的 IO，写库与通知互不影响；</li>
 *   <li><b>best-effort</b>：通知写 Redis 失败只告警，不影响 DB 写入与重试；</li>
 *   <li><b>可重建</b>：未读计数可从 like_record/visit_record 重算，Redis 丢失可兜底。</li>
 * </ul>
 *
 * 数据结构：未读计数 String(INCR) + 最近通知 ZSet（score=时间戳，裁剪保留 50 条）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionNotificationService {

    public static final String TYPE_LIKE = "like";
    public static final String TYPE_VISIT = "visit";

    private static final int LIST_MAX = 50;
    private static final long LIST_TTL_DAYS = 7;

    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 批量写互动通知（未读计数 + 最近列表）。@Async：在写库事务提交后异步执行，不阻塞主流程。
     */
    @Async
    public void notifyBatch(List<LikeRecord> likes, List<VisitRecord> visits) {
        try {
            if (likes != null) {
                for (LikeRecord l : likes) {
                    incrUnread(l.getToUserId(), TYPE_LIKE);
                    addRecent(l.getToUserId(), TYPE_LIKE, l.getFromUserId());
                }
            }
            if (visits != null) {
                for (VisitRecord v : visits) {
                    incrUnread(v.getToUserId(), TYPE_VISIT);
                    addRecent(v.getToUserId(), TYPE_VISIT, v.getFromUserId());
                }
            }
        } catch (Exception e) {
            log.warn("InteractionNotificationService.notifyBatch failed (best-effort, DB unaffected)", e);
        }
    }

    /** 查询未读计数：{like: n, visit: n}。 */
    public Map<String, Long> getUnread(Long userId) {
        Map<String, Long> result = new HashMap<>();
        result.put(TYPE_LIKE, readUnread(userId, TYPE_LIKE));
        result.put(TYPE_VISIT, readUnread(userId, TYPE_VISIT));
        return result;
    }

    /** 标记已读：清零未读计数（保留最近通知列表历史）。 */
    public void markRead(Long userId) {
        redisTemplate.delete(keyBuilder.notifUnread(userId, TYPE_LIKE));
        redisTemplate.delete(keyBuilder.notifUnread(userId, TYPE_VISIT));
    }

    /** 最近互动通知（倒序，最多 LIST_MAX 条），member = "like:{fromUserId}" / "visit:{fromUserId}"。 */
    public Set<String> recentNotifs(Long userId) {
        return redisTemplate.opsForZSet().reverseRange(keyBuilder.notifList(userId), 0, LIST_MAX - 1);
    }

    private void incrUnread(Long toUserId, String type) {
        redisTemplate.opsForValue().increment(keyBuilder.notifUnread(toUserId, type));
    }

    private long readUnread(Long userId, String type) {
        String v = redisTemplate.opsForValue().get(keyBuilder.notifUnread(userId, type));
        return v == null ? 0L : Long.parseLong(v);
    }

    private void addRecent(Long toUserId, String type, Long fromUserId) {
        String listKey = keyBuilder.notifList(toUserId);
        long ts = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(listKey, type + ":" + fromUserId, (double) ts);
        Long size = redisTemplate.opsForZSet().zCard(listKey);
        if (size != null && size > LIST_MAX) {
            // 裁剪最老（rank [0, size-max)），等价 ZREMRANGEBYRANK
            redisTemplate.opsForZSet().removeRange(listKey, 0, size - LIST_MAX - 1);
        }
        redisTemplate.expire(listKey, LIST_TTL_DAYS, TimeUnit.DAYS);
    }
}
