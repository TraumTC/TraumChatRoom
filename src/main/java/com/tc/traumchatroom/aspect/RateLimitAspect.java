package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 接口限流 AOP 切面
 *
 * 固定窗口实现（窗口边界可能出现突刺，但计数与过期原子完成）：
 * 1. 用 Redis String 存储当前窗口的请求计数（Lua 原子 INCR + 首次设 TTL）
 * 2. 每次请求检查计数是否超过限制
 * 3. 超过限制则拒绝请求，返回 429
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** 原子计数 + 过期 Lua：key 不存在时置 1 并设 TTL，否则自增且首个计数保证带 TTL */
    private static final String RATE_LIMIT_LUA =
            "local cur = redis.call('GET', KEYS[1]) " +
            "if cur == false then " +
            "  redis.call('SET', KEYS[1], 1, 'PX', tonumber(ARGV[2])) " +
            "  return 1 " +
            "end " +
            "local n = redis.call('INCR', KEYS[1]) " +
            "if n == 1 then " +
            "  redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2])) " +
            "end " +
            "return n";

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 获取当前用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        // 构造 Redis key
        String key = "chat:rate:" + rateLimit.key() + ":" + username;

        // 原子计数 + 过期（避免 get+increment+expire 竞态）
        Long newCount = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class),
                java.util.List.of(key),
                String.valueOf(rateLimit.maxRequests()), String.valueOf(rateLimit.windowMillis())
        );

        long count = newCount != null ? newCount : 1;
        if (count > rateLimit.maxRequests()) {
            log.warn("用户 {} 触发限流: key={}, count={}", username, rateLimit.key(), count);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        // 执行原方法
        return point.proceed();
    }
}
