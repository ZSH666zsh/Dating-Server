package com.dating.match.service;

import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.constant.Source;
import com.dating.match.entity.MatchEntity;
import com.dating.match.manager.MatchManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 匹配管理服务。
 * 对应 match-service-prd-tech.md §5.3 Match 创建副作用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchManager matchManager;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * 创建匹配（供 DhDelayedMatchService 延迟回调时调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createMatch(Long userA, Long userB, String source) {
        Long matchId = idGenerator.nextId();
        int rows = matchManager.createMatch(matchId, userA, userB, source);

        if (rows == 0) {
            var existing = matchManager.getByPair(userA, userB);
            if (existing != null) {
                log.warn("Duplicate match attempt: pair=({}, {}), existing_id={}",
                        userA, userB, existing.getId());
                return existing.getId();
            }
        }

        log.info("Match created: id={} pair=({}, {}) source={}", matchId, userA, userB, source);
        return matchId;
    }

    /**
     * 获取匹配列表。
     */
    public List<MatchEntity> listMatches(Long userId, int pageSize, Long cursor) {
        return matchManager.listMatches(userId, pageSize, cursor);
    }
}
