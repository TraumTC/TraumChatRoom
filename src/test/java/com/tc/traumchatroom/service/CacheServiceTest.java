package com.tc.traumchatroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CacheService 单元测试（Cache-Aside 读/写/失效）
 */
@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private CacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // @InjectMocks 不会注入普通字段，手动注入 ObjectMapper
        ReflectionTestUtils.setField(cacheService, "objectMapper", objectMapper);
    }

    private User user(Integer id) {
        User u = new User();
        u.setId(id);
        u.setUsername("zhangsan");
        u.setName("张三");
        u.setPassword("secret-password"); // 敏感字段，不应进缓存
        u.setRole("ROLE_USER");
        u.setStatus(1);
        return u;
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockOps() {
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        return ops;
    }

    @Test
    void cacheUserStoresWithoutPassword() {
        ValueOperations<String, String> ops = mockOps();
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        cacheService.cacheUser(user(1));

        verify(ops).set(eq("chat:user:1"), captor.capture(), any(Duration.class));
        // 缓存内容不应包含密码字段
        assertThat(captor.getValue()).doesNotContain("secret-password");
        assertThat(captor.getValue()).contains("张三");
    }

    @Test
    void cacheHitReturnsCachedUserWithoutDbAccess() throws Exception {
        ValueOperations<String, String> ops = mockOps();
        User u = user(1);
        u.setPassword(null);
        when(ops.get("chat:user:1")).thenReturn(objectMapper.writeValueAsString(u));

        User result = cacheService.getUserById(1);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
        // 缓存命中 → 不查数据库
        verify(userMapper, never()).findById(any());
    }

    @Test
    void cacheMissFetchesFromDbAndBackfills() throws Exception {
        ValueOperations<String, String> ops = mockOps();
        when(ops.get("chat:user:1")).thenReturn(null);
        User u = user(1);
        u.setPassword(null);
        when(userMapper.findById(1)).thenReturn(u);

        User result = cacheService.getUserById(1);

        assertThat(result).isNotNull();
        verify(userMapper).findById(1);
        // 回填缓存
        verify(ops).set(eq("chat:user:1"), anyString(), any(Duration.class));
    }

    @Test
    void evictUserDeletesCache() {
        cacheService.evictUser(1);
        verify(redisTemplate).delete("chat:user:1");
    }

    @Test
    void nullIdIsSafe() {
        cacheService.evictUser(null);
        verify(redisTemplate, never()).delete(anyString());
        assertThat(cacheService.getUserById(null)).isNull();
    }
}
