package com.dating.im.repository;

import com.dating.im.entity.UserOnlineSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

/** 在线会话 repository。 */
public interface UserOnlineSessionRepository extends JpaRepository<UserOnlineSession, Long> {

    /** 查询在指定时间范围内下线的用户 ID（去重）。 */
    @Query("SELECT DISTINCT s.userId FROM UserOnlineSession s " +
           "WHERE s.offlineAt BETWEEN ?1 AND ?2 AND s.deleted = false")
    List<Long> findDistinctUserIdsByOfflineAtBetween(OffsetDateTime since, OffsetDateTime until);
}
