package com.tc.traumchatroom.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解（防重复提交）
 *
 * 使用方式：在 POST/PUT 等写接口上添加注解，客户端请求需携带 header `X-Request-Id`（前端生成 UUID）。
 * 同一用户在防重窗口内携带相同 X-Request-Id 的重复请求会被拦截。
 *
 * <pre>
 * {@code
 * @Idempotent(key = "friend-apply", timeout = 5)
 * @PostMapping("/api/friend/request")
 * public Result<Void> apply(...) { ... }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务标识，用于区分不同接口，如 friend-apply / file-upload */
    String key();

    /** 防重窗口（秒），默认 5 秒 */
    int timeout() default 5;
}
