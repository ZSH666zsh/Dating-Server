package com.dating.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
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
 * HealthController 单元测试：mock 依赖，验证 UP/DOWN 各分支行为。
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void healthReturnsUp_whenAllDependenciesOk() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(1);
        when(stringRedisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("user-service"));
    }

    @Test
    void healthReturnsDown_whenMysqlFails() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("mysql down"));
        when(stringRedisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }

    @Test
    void healthReturnsDown_whenRedisFails() throws Exception {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(1);
        when(stringRedisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }
}
