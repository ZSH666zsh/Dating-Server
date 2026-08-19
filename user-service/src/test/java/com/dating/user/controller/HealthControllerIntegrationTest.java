package com.dating.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /health 端到端（E2E）集成测试。
 *
 * 通过真实 Spring MVC 装配（MockMvc + Jackson 序列化）请求 GET /health，
 * 验证整条 HTTP 链路：请求 → Controller → Result 序列化 → JSON 响应。
 *
 * 说明：本项目完整应用上下文强耦合外部配置（Nacos / S3 / 真实 DB/Redis），
 * 不适合在无外部环境时启动完整上下文。本测试聚焦 HealthController 及其真实
 * MVC 装配，依赖以 mock 隔离，可独立重复运行。
 */
@SpringBootTest(classes = HealthController.class, properties = "spring.config.import=")
@AutoConfigureMockMvc
@ImportAutoConfiguration({WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class})
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void healthEndpointReturnsUp_whenDependenciesOk() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(1);
        when(stringRedisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("user-service"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.timestamp").exists());
    }

    @Test
    void healthEndpointReturnsDown_whenRedisUnavailable() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(1);
        when(stringRedisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }
}
