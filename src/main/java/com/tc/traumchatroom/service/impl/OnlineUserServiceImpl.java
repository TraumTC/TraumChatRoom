package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.service.OnlineUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户服务实现
 * 使用 Redis Set 存储在线用户
 */
@Slf4j
@Service
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_USERS_KEY = "chat:online:users";
    private static final long HEARTBEAT_TIMEOUT = 30; // 心跳超时 30 秒

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void userOnline(String username) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
        // 设置整个 key 的过期时间（每次有用户上线时刷新）
        redisTemplate.expire(ONLINE_USERS_KEY, HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);
        log.debug("用户上线: {}", username);
    }

    @Override
    public void userOffline(String username) {
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);
        log.debug("用户下线: {}", username);
    }

    @Override
    public Set<String> getOnlineUsers() {
        return redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
    }

    @Override
    public int getOnlineCount() {
        Long count = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public boolean isOnline(String username) {
        Boolean member = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, username);
        return member != null && member;
    }

    @Override
    public void updateHeartbeat(String username) {
        // 刷新在线用户的过期时间
        redisTemplate.expire(ONLINE_USERS_KEY, HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);
    }
}
