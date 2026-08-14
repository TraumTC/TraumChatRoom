package com.tc.traumchatroom.util;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 限流器 — 统一固定窗口限流实现
 *
 * 合并原先散落在 RateLimitAspect / WebSocketChatController / AiServiceImpl 三处几乎相同的
 * "不存在则 SET+TTL，否则 INCR" Lua 脚本，单一实现、语义一致：
 * - key 不存在 → SET 1 并设 TTL（窗口秒）
 * - key 存在 → INCR；若 INCR 后 == 1 则补设 TTL（防止并发下首计数丢失过期）
 * 全部操作由 Lua 原子完成，避免 get+increment+expire 竞态。
 */
@Component
public class RedisRateLimiter {

    /** 原子计数 + 过期 Lua（窗口单位：秒） */
    private static final String RATE_LIMIT_LUA =
            "local cur = redis.call('GET', KEYS[1]) " +
            "if cur == false then " +
            "  redis.call('SET', KEYS[1], 1, 'EX', tonumber(ARGV[1])) " +
            "  return 1 " +
            "end " +
            "local n = redis.call('INCR', KEYS[1]) " +
            "if n == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +
            "end " +
            "return n";

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 尝试获取一次配额
     *
     * @param key          限流 key（业务方自带前缀，如 chat:send:rate:xxx）
     * @param maxRequests  窗口内最大请求数
     * @param windowSeconds 窗口时长（秒）
     * @return true 允许放行；false 超限
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        Long count = redisTemplate.execute(
                new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class),
                List.of(key),
                String.valueOf(windowSeconds)
        );
        long current = count != null ? count : 1;
        return current <= maxRequests;
    }
}
