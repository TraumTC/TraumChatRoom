package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.Idempotent;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 接口幂等切面（基于 Redis SETNX）
 *
 * 原理：
 * 1. 从请求头 X-Request-Id 取唯一请求号（前端生成 UUID，同一操作复用同一个号）
 * 2. Redis `SET key value NX EX timeout` 抢占 —— 只有第一次能占成功
 * 3. 重复请求（同用户 + 同 key + 同 requestId）命中已存在的 key → 拦截，返回 429
 * 4. 业务执行异常时删除 key，释放防重窗口，允许重试
 *
 * 为什么用 Redis 而非 JVM 本地锁？
 * - 项目可能多实例部署，本地 ConcurrentHashMap 无法跨实例生效
 * - SETNX 是原子操作，天然避免并发抢占
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private HttpServletRequest request;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 未携带 X-Request-Id 则放行（兼容旧客户端）
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return joinPoint.proceed();
        }

        // 2. 组装防重 key：用户 + 业务 + 请求号
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "anonymous";
        String key = "chat:idempotent:" + username + ":" + idempotent.key() + ":" + requestId;

        // 3. SETNX 抢占
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, "1", Duration.ofSeconds(idempotent.timeout())
        );

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请勿重复提交，请稍后再试");
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            // 4. 业务失败释放防重窗口，允许重试
            redisTemplate.delete(key);
            throw t;
        }
    }
}
