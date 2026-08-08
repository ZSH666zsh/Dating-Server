package com.dating.post.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 生成器
 * workerId 通过 Nacos 配置注入，不同实例用不同 workerId 防碰撞
 *
 * datacenter-id=0，和 user 错开
 */
@Configuration
public class SnowflakeIdGenerator {

    @Value("${snowflake.worker-id:1}")
    private long workerId;

    @Value("${snowflake.datacenter-id:0}")
    private long datacenterId;

    @Bean
    public SnowflakeIdGenerator.Snowflake snowflake() {
        return new Snowflake(workerId, datacenterId);
    }

    /**
     * 简版雪花算法（Twitter Snowflake 风格）
     * 41 位时间戳 + 10 位 workerId + 12 位序列号
     */
    public static class Snowflake {
        private static final long EPOCH = 1700000000000L;
        private static final long WORKER_ID_BITS = 10L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
        private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

        private final long workerId;
        private long lastTimestamp = -1L;
        private long sequence = 0L;

        public Snowflake(long workerId, long datacenterId) {
            long combined = (datacenterId << (WORKER_ID_BITS / 2)) | workerId;
            if (combined < 0 || combined > MAX_WORKER_ID) {
                throw new IllegalArgumentException("workerId out of range: " + combined);
            }
            this.workerId = combined;
        }

        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp) {
                timestamp = lastTimestamp;
            }
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    while ((timestamp = System.currentTimeMillis()) <= lastTimestamp) {
                        Thread.yield();
                    }
                }
            } else {
                sequence = 0;
            }
            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }
    }
}
