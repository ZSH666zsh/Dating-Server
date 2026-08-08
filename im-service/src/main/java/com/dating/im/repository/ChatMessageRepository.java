package com.dating.im.repository;

import com.dating.im.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** 消息流水 repository（JPA，非 MyBatis-Plus）。 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
}
