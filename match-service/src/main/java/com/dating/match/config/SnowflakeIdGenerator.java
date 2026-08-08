package com.dating.match.config;

import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器（匹配场景使用 match_{yyyymm}_{seq} 格式，此处保留备用）。
 */
@Component
public class SnowflakeIdGenerator {

    // 使用简单的 System.currentTimeMillis() + 自增序列
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF;
            if (sequence == 0) {
                Thread.yield();
                timestamp = System.currentTimeMillis();
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - 1735689600000L) << 12) | sequence;
    }
}
