package com.dating.im.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 消息流水。对应 im-service-design.md §10.1。使用 JPA（不是 MyBatis-Plus）。 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_msg_from", columnList = "fromUserId"),
    @Index(name = "idx_msg_to", columnList = "toUserId")
})
public class ChatMessage {

    @Id
    private String messageId;
    private long fromUserId;
    private long toUserId;

    @Column(columnDefinition = "TEXT")
    private String content;
    private String type;
    private String conversationType;
    private String provider;
    private String routeType;
    private long timestamp;

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // ─── getters ───
    public String getMessageId() { return messageId; }
    // ─── setters ───
    public void setMessageId(String v) { this.messageId = v; }
    public void setFromUserId(long v) { this.fromUserId = v; }
    public void setToUserId(long v) { this.toUserId = v; }
    public void setContent(String v) { this.content = v; }
    public void setType(String v) { this.type = v; }
    public void setProvider(String v) { this.provider = v; }
    public void setRouteType(String v) { this.routeType = v; }
    public void setTimestamp(long v) { this.timestamp = v; }
}
