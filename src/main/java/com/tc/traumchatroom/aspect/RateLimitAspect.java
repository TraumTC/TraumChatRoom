package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.util.RedisRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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

    @Resource
    private RedisRateLimiter redisRateLimiter;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 获取当前用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        // 构造 Redis key
        String key = "chat:rate:" + rateLimit.key() + ":" + username;

        // 原子计数 + 过期（窗口毫秒转秒，向上取整保证窗口不小于原值）
        long windowSeconds = (rateLimit.windowMillis() + 999) / 1000;
        if (!redisRateLimiter.tryAcquire(key, rateLimit.maxRequests(), windowSeconds)) {
            log.warn("用户 {} 触发限流: key={}", username, rateLimit.key());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        // 执行原方法
        return point.proceed();
    }
}
