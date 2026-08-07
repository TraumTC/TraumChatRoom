package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.service.OnlineUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * 在线用户服务实现
 * 使用 Redis Sorted Set 存储在线用户（score = 最后心跳时间戳）
 */
@Slf4j
@Service
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_USERS_KEY = "chat:online:users";
    /** 心跳超时：5 分钟（300000 毫秒）无心跳视为离线 */
    private static final long HEARTBEAT_TIMEOUT_MS = 5 * 60 * 1000;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void userOnline(String username) {
        redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, username, System.currentTimeMillis());
        log.debug("用户上线: {}", username);
    }

    @Override
    public void userOffline(String username) {
        redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, username);
        log.debug("用户下线: {}", username);
    }

    @Override
    public Set<String> getOnlineUsers() {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TIMEOUT_MS;
        // 获取最近 5 分钟内有心跳的用户
        Set<String> users = redisTemplate.opsForZSet().rangeByScore(ONLINE_USERS_KEY, cutoff, Double.MAX_VALUE);
        return users != null ? users : Collections.emptySet();
    }

    @Override
    public int getOnlineCount() {
        return getOnlineUsers().size();
    }

    @Override
    public boolean isOnline(String username) {
        Double score = redisTemplate.opsForZSet().score(ONLINE_USERS_KEY, username);
        if (score == null) return false;
        return (System.currentTimeMillis() - score) < HEARTBEAT_TIMEOUT_MS;
    }

    @Override
    public void updateHeartbeat(String username) {
        redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, username, System.currentTimeMillis());
    }
}
