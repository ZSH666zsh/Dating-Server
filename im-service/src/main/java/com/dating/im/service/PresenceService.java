package com.dating.im.service;

import com.dating.im.entity.UserOnlineSession;
import com.dating.im.model.event.UserOfflineEvent;
import com.dating.im.model.event.UserOnlineEvent;
import com.dating.im.repository.UserOnlineSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线状态服务。对应 im-service-design.md §7。
 *
 * <p>im-service 是全平台在线状态的唯一权威源。
 * OpenIM 上下线回调驱动，Redis ZSet 存实时态，PG 会话表存历史态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceService {

    private static final String ONLINE_ZSET = "im:presence:online";

    private final StringRedisTemplate redisTemplate;
    private final UserOnlineSessionRepository sessionRepo;

    /** 用户上线。 */
    public void online(UserOnlineEvent event) {
        // ZADD NX：已在线则不覆盖 score
        boolean added = redisTemplate.opsForZSet().addIfAbsent(
                ONLINE_ZSET, String.valueOf(event.userId()), event.timestampMs());

        if (added) {
            // 首次上线，开 PG 会话
            UserOnlineSession session = new UserOnlineSession();
            session.setUserId(event.userId());
            session.setPlatform(event.platform());
            session.setOnlineAt(OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(event.timestampMs()), ZoneOffset.UTC));
            sessionRepo.save(session);
            log.debug("User online: userId={}", event.userId());
        }
    }

    /** 用户下线。 */
    public void offline(UserOfflineEvent event) {
        Double score = redisTemplate.opsForZSet().score(ONLINE_ZSET, String.valueOf(event.userId()));
        if (score != null) {
            long sinceMs = score.longValue();
            long durationSeconds = (event.timestampMs() - sinceMs) / 1000;

            // 回填 PG 会话
            var sessions = sessionRepo.findAll();
            for (var s : sessions) {
                if (s.getUserId() == event.userId() && s.getOfflineAt() == null) {
                    s.setOfflineAt(OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(event.timestampMs()), ZoneOffset.UTC));
                    s.setDurationSeconds(Math.max(0, durationSeconds));
                    sessionRepo.save(s);
                    break;
                }
            }

            // 移出在线集合
            redisTemplate.opsForZSet().remove(ONLINE_ZSET, String.valueOf(event.userId()));
            log.debug("User offline: userId={} duration={}s", event.userId(), durationSeconds);
        }
    }

    /** 查询本窗口内新上线的用户 ID。0。对应 im-service-design.md §7.3。 */
    public List<Long> listOnlineUserIds(long sinceMs, long untilMs, int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), 50000);
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(
                ONLINE_ZSET, sinceMs, untilMs, 0, clampedLimit);
        return members.stream()
                .map(this::safeParseLong)
                .filter(id -> id > 0)
                .toList();
    }

    /** 查询本窗口内已下线的用户 ID。 */
    public List<Long> listRecentOfflineUsers(long sinceMs, long untilMs, int limit) {
        int clampedLimit = Math.min(Math.max(limit, 1), 50000);
        OffsetDateTime since = Instant.ofEpochMilli(sinceMs).atOffset(ZoneOffset.UTC);
        OffsetDateTime until = Instant.ofEpochMilli(untilMs).atOffset(ZoneOffset.UTC);
        return sessionRepo.findDistinctUserIdsByOfflineAtBetween(since, until)
                .stream()
                .limit(clampedLimit)
                .toList();
    }

    /** 孤儿会话清扫（只上线没下线的兜底）。 */
    public void sweepOrphans() {
        long thresholdMs = System.currentTimeMillis() - 26L * 3600 * 1000;
        Set<String> orphans = redisTemplate.opsForZSet().rangeByScore(ONLINE_ZSET, 0, thresholdMs);
        for (String userIdStr : orphans) {
            Double score = redisTemplate.opsForZSet().score(ONLINE_ZSET, userIdStr);
            if (score != null) {
                long onlineSinceMs = score.longValue();
                long duration = (System.currentTimeMillis() - onlineSinceMs) / 1000;
                // 强制下线
                var sessions = sessionRepo.findAll();
                for (var s : sessions) {
                    if (s.getUserId() == Long.parseLong(userIdStr) && s.getOfflineAt() == null) {
                        s.setOfflineAt(OffsetDateTime.now());
                        s.setDurationSeconds(Math.min(duration, 26L * 3600));
                        sessionRepo.save(s);
                        break;
                    }
                }
                redisTemplate.opsForZSet().remove(ONLINE_ZSET, userIdStr);
            }
        }
        if (!orphans.isEmpty()) {
            log.info("Presence sweep: cleaned {} orphan sessions", orphans.size());
        }
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1; }
    }
}
