package com.tc.traumchatroom.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * 使用方式：在 Controller 方法上添加此注解
 * @LogOperation(action = "DELETE_USER", targetType = "user")
 * @DeleteMapping("/api/admin/users/{id}")
 * public Result<?> deleteUser(@PathVariable Integer id) { ... }
 *
 * AOP 会自动记录操作日志到 operation_log 表
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
    /** 操作类型：LOGIN / DELETE_USER / RECALL_MESSAGE 等 */
    String action();

    /** 目标类型：user / message / file 等 */
    String targetType() default "";
}
