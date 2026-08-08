package com.dating.match.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:38.76.188.242}")
    private String redisHost;

    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:1}")
    private int database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8);
        return Redisson.create(config);
    }
}
