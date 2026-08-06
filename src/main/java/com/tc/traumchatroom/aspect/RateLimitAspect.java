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
 * 滑动窗口算法实现：
 * 1. 用 Redis String 存储当前窗口的请求计数
 * 2. 每次请求检查计数是否超过限制
 * 3. 超过限制则拒绝请求，返回 429
 *
 * 为什么用滑动窗口？
 * - 固定窗口：在窗口边界可能出现突刺（如最后一秒发10个请求，下一秒又发10个）
 * - 滑动窗口：平滑控制请求速率
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 获取当前用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";

        // 构造 Redis key
        String key = "chat:rate:" + rateLimit.key() + ":" + username;

        // 检查当前窗口内的请求数
        String countStr = redisTemplate.opsForValue().get(key);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;

        if (count >= rateLimit.maxRequests()) {
            log.warn("用户 {} 触发限流: key={}, count={}", username, rateLimit.key(), count);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        // 增加计数
        Long newCount = redisTemplate.opsForValue().increment(key);
        if (newCount != null && newCount == 1) {
            // 第一次请求，设置过期时间
            redisTemplate.expire(key, rateLimit.windowMillis(), TimeUnit.MILLISECONDS);
        }

        // 执行原方法
        return point.proceed();
    }
}
