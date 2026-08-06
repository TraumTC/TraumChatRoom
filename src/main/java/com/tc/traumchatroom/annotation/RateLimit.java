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
 * 基于 Redis 滑动窗口算法实现
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 限流标识（用于 Redis key） */
    String key() default "default";

    /** 窗口内最大请求数 */
    int maxRequests() default 10;

    /** 窗口时间（毫秒），默认 1 秒 */
    long windowMillis() default 1000;
}
