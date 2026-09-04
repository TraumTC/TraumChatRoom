package com.tc.traumchatroom.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * 使用方式：
 * @RateLimit(key = "upload", maxRequests = 5, windowMillis = 60000)
 * @PostMapping("/api/file/upload")
 * public Result<?> upload(...) { ... }
 *
 * 匿名接口须显式指定 by = By.IP，见 {@link By#IP}。
 *
 * 基于 Redis 固定窗口实现（窗口边界可能出现突刺，计数与过期由 Lua 原子完成，
 * 见 {@link com.tc.traumchatroom.util.RedisRateLimiter}）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流主体维度：决定 Redis key 后缀取什么。
     */
    enum By {
        /** 按登录用户名（默认，适用于已认证接口） */
        USER,

        /**
         * 按来源 IP。
         *
         * 匿名接口（注册、游客登录）必须用这个：它们是 permitAll，
         * Spring Security 的 AnonymousAuthenticationFilter 会塞一个
         * AnonymousAuthenticationToken，{@code getName()} 恒为 "anonymousUser"。
         * 此时按 USER 限流会退化成全站共用一个配额 —— 任何人刷满，
         * 所有人都被挡在门外，防滥用变成了自伤。
         *
         * 有效性依赖 IpUtil 能拿到真实 IP：未配置 app.trusted-proxies 时
         * X-Forwarded-For 被无条件信任，攻击者每个请求换一个伪造头即可绕过。
         */
        IP
    }

    /** 限流标识（用于 Redis key） */
    String key() default "default";

    /** 窗口内最大请求数 */
    int maxRequests() default 10;

    /** 窗口时间（毫秒），默认 1 秒 */
    long windowMillis() default 1000;

    /** 限流主体维度，默认按用户名（保持既有接口行为不变） */
    By by() default By.USER;
}
