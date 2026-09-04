package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.util.IpUtil;
import com.tc.traumchatroom.util.RedisRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 接口限流 AOP 切面
 *
 * 固定窗口实现（窗口边界可能出现突刺，但计数与过期原子完成）：
 * 1. 用 Redis String 存储当前窗口的请求计数（Lua 原子 INCR + 首次设 TTL）
 * 2. 每次请求检查计数是否超过限制
 * 3. 超过限制则拒绝请求，返回 429
 *
 * 限流主体由 {@link RateLimit#by()} 决定：已认证接口按用户名，
 * 匿名接口（注册/游客登录）按来源 IP —— 详见 {@link RateLimit.By#IP} 的说明。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** 主体解析不出时的兜底值（IP 拿不到 / 无安全上下文），此时退化为该维度的全局配额 */
    private static final String UNKNOWN_SUBJECT = "unknown";

    @Resource
    private RedisRateLimiter redisRateLimiter;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 解析限流主体（用户名 or 来源 IP）
        String subject = switch (rateLimit.by()) {
            case IP -> resolveClientIp();
            case USER -> resolveUsername();
        };

        // 构造 Redis key。带上维度前缀：同一个 key() 在两种维度下的计数互不污染，
        // 也便于运维直接从 key 看出这条配额是按谁算的。
        String key = "chat:rate:" + rateLimit.key() + ":"
                + rateLimit.by().name().toLowerCase() + ":" + subject;

        // 原子计数 + 过期（窗口毫秒转秒，向上取整保证窗口不小于原值）
        long windowSeconds = (rateLimit.windowMillis() + 999) / 1000;
        if (!redisRateLimiter.tryAcquire(key, rateLimit.maxRequests(), windowSeconds)) {
            log.warn("触发限流: key={}, by={}, subject={}",
                    rateLimit.key(), rateLimit.by(), subject);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        // 执行原方法
        return point.proceed();
    }

    /** 当前登录用户名；无安全上下文时按匿名处理 */
    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    /**
     * 来源 IP，统一走 IpUtil —— 与登录锁定、审计日志共用同一套可信代理策略，
     * 避免同一个请求在限流和审计里被算成两个不同的 IP。
     *
     * 拿不到 Servlet 请求上下文（非 Web 调用）时返回兜底值：被注解的都是 HTTP 接口，
     * 正常不会走到这里，但退化成全局配额也比抛异常挡掉正常请求好。
     */
    private String resolveClientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            log.warn("限流按 IP 但无 Servlet 请求上下文，退化为全局配额");
            return UNKNOWN_SUBJECT;
        }
        String ip = IpUtil.fromHttp(servletAttrs.getRequest());
        return StringUtils.hasText(ip) ? ip : UNKNOWN_SUBJECT;
    }
}
