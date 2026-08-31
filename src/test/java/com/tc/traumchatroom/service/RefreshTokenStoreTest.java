package com.tc.traumchatroom.service;

import com.tc.traumchatroom.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenStoreTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JwtUtil jwtUtil;
    private RefreshTokenStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-256bit-0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 30 * 60 * 1000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);

        store = new RefreshTokenStore();
        ReflectionTestUtils.setField(store, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(store, "jwtUtil", jwtUtil);
    }

    @Test
    void newTokenUsesAtomicHashedSessionStorage() {
        String token = jwtUtil.generateRefreshToken("alice", "device-a");
        store.save("alice", token, Duration.ofDays(7));

        verify(redisTemplate).execute(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
