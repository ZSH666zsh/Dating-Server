package com.dating.match.service;

import com.dating.match.client.UserServiceClient;
import com.dating.match.entity.LikeRecord;
import com.dating.match.entity.VisitRecord;
import com.dating.match.manager.LikeRecordManager;
import com.dating.match.manager.VisitRecordManager;
import com.dating.match.config.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Like / Visit 互动记录服务。
 * 对应 match-service-prd-tech.md §6。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeVisitService {

    private final LikeRecordManager likeRecordManager;
    private final VisitRecordManager visitRecordManager;
    private final UserServiceClient userClient;
    private final SnowflakeIdGenerator idGenerator;
    private final InteractionNotificationService notificationService;

    /**
     * 谁 Like 了我。
     */
    public List<LikeRecord> listLikesOfMe(Long userId, int pageSize, Long cursor) {
        return likeRecordManager.listLikesOfMe(userId, pageSize, cursor);
    }

    /**
     * 谁访问了我。
     */
    public List<VisitRecord> listVisitsOfMe(Long userId, int pageSize, Long cursor) {
        return visitRecordManager.listVisitsOfMe(userId, pageSize, cursor);
    }

    /**
     * 互动未读计数（like / visit）。
     */
    public Map<String, Long> getUnreadCount(Long userId) {
        return notificationService.getUnread(userId);
    }

    /**
     * 标记互动已读（清零未读计数）。
     */
    public void markNotifRead(Long userId) {
        notificationService.markRead(userId);
    }

    /**
     * 上报主页访问（异步落 visit_record）。
     */
    public void recordVisit(Long viewerUserId, Long targetUserId) {
        if (viewerUserId.equals(targetUserId)) {
            log.debug("RecordVisit self-visit skipped: userId={}", viewerUserId);
            return; // 自访问短路
        }

        VisitRecord record = new VisitRecord();
        record.setId(idGenerator.nextId());
        record.setFromUserId(viewerUserId);
        record.setToUserId(targetUserId);
        record.setFromUserType(1); // BH
        record.setSource(1); // PROFILE_VIEW
        record.setVisitedAt(OffsetDateTime.now());
        visitRecordManager.upsert(record);

        log.debug("RecordVisit: viewer={} target={}", viewerUserId, targetUserId);
    }
}
