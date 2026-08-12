package com.dating.post.mq;

/**
 * 写扩散消息体（发帖 → 关注者 timeline）。
 *
 * @param postId      新帖业务主键（雪花 ID，全局唯一）
 * @param ownerUserId 发帖人 user_id
 * @param createdAt   发帖时间戳（毫秒）——作为 timeline ZSet 的 score，
 *                    必须是固定业务时间，消息重投时不变 → ZADD 覆盖语义成立（幂等）
 */
public record PostFanoutMessage(Long postId, Long ownerUserId, Long createdAt) {
}
