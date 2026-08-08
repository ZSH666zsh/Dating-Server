package com.dating.im.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 用户在线会话。对应 im-service-design.md §10.1。 */
@Entity
@Table(name = "user_online_session", indexes = {
    @Index(name = "idx_session_user", columnList = "userId"),
    @Index(name = "idx_session_offline", columnList = "offlineAt", unique = false)
})
public class UserOnlineSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;

    private int platform;

    private OffsetDateTime onlineAt;

    private OffsetDateTime offlineAt;      // null = 还在线

    private Long durationSeconds;          // 下线时回填, null = 还在线

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();

    private boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public int getPlatform() { return platform; }
    public void setPlatform(int platform) { this.platform = platform; }
    public OffsetDateTime getOnlineAt() { return onlineAt; }
    public void setOnlineAt(OffsetDateTime onlineAt) { this.onlineAt = onlineAt; }
    public OffsetDateTime getOfflineAt() { return offlineAt; }
    public void setOfflineAt(OffsetDateTime offlineAt) { this.offlineAt = offlineAt; }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
