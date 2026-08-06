package com.tc.traumchatroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 缓存服务（Cache-Aside 模式）
 *
 * 用户信息缓存：
 * - 读：先查 Redis（key: chat:user:{id}），未命中再查数据库并回填
 * - 写：登录成功后写入
 * - 失效：修改资料 / 头像时主动删除，保证一致性
 *
 * 为什么用 Cache-Aside 而不是 Cache-Through？
 * - 缓存由业务代码显式控制读/写/失效，简单直接
 * - 聊天室用户信息是"读多写少"场景，命中率高
 * - 失效时机可控：只在用户信息变更时删除，避免脏数据
 */
@Slf4j
@Service
public class CacheService {

    private static final String USER_CACHE_KEY = "chat:user:";
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(30);

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 缓存用户信息（去除密码等敏感字段）
     */
    public void cacheUser(User user) {
        if (user == null || user.getId() == null) return;

        User safe = new User();
        safe.setId(user.getId());
        safe.setUsername(user.getUsername());
        safe.setName(user.getName());
        safe.setAvatar(user.getAvatar());
        safe.setRole(user.getRole());
        safe.setStatus(user.getStatus());
        safe.setLastActiveTime(user.getLastActiveTime());

        try {
            redisTemplate.opsForValue().set(
                    USER_CACHE_KEY + user.getId(),
                    objectMapper.writeValueAsString(safe),
                    USER_CACHE_TTL
            );
        } catch (Exception e) {
            log.warn("缓存用户失败: id={}", user.getId(), e);
        }
    }

    /**
     * 失效用户缓存（用户信息变更后调用，保证一致性）
     */
    public void evictUser(Integer id) {
        if (id != null) {
            redisTemplate.delete(USER_CACHE_KEY + id);
        }
    }

    /**
     * 获取用户：先查缓存，未命中查数据库并回填
     */
    public User getUserById(Integer id) {
        if (id == null) return null;

        // 1. 查缓存
        String json = redisTemplate.opsForValue().get(USER_CACHE_KEY + id);
        if (json != null) {
            try {
                return objectMapper.readValue(json, User.class);
            } catch (Exception e) {
                log.warn("反序列化用户缓存失败: id={}", id, e);
            }
        }

        // 2. 缓存未命中 → 查数据库并回填
        User user = userMapper.findById(id);
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }
}
