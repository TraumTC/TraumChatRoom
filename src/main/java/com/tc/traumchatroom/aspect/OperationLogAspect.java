package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.entity.OperationLog;
import com.tc.traumchatroom.mapper.OperationLogMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志 AOP 切面
 *
 * 工作原理：
 * 1. 拦截所有标注了 @LogOperation 的方法
 * 2. 方法执行成功后自动记录操作日志
 * 3. 记录操作者、操作类型、目标、IP、UA 等信息
 *
 * 为什么用 AOP？
 * - 业务代码不需要写日志记录逻辑
 * - 统一日志格式
 * - 方便后续扩展（如发送告警）
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private HttpServletRequest request;

    /**
     * 方法执行成功后记录日志
     */
    @AfterReturning(pointcut = "@annotation(com.tc.traumchatroom.annotation.LogOperation)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogOperation logAnnotation = method.getAnnotation(LogOperation.class);

            if (logAnnotation == null) return;

            // 获取当前用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "anonymous";

            // 构造操作日志
            OperationLog operationLog = new OperationLog();
            operationLog.setUsername(username);
            operationLog.setAction(logAnnotation.action());
            operationLog.setTargetType(logAnnotation.targetType());

            // 获取目标ID（从方法参数中提取）
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    if (arg instanceof Long || arg instanceof Integer) {
                        operationLog.setTargetId(((Number) arg).longValue());
                        break;
                    }
                }
            }

            // 获取客户端 IP
            operationLog.setIp(getClientIp());

            // 获取 User-Agent
            String userAgent = request.getHeader("User-Agent");
            operationLog.setUserAgent(userAgent != null ? userAgent.substring(0, Math.min(500, userAgent.length())) : null);

            // 保存到数据库
            operationLogMapper.insert(operationLog);

            log.debug("操作日志: {} - {} - {}", username, logAnnotation.action(), logAnnotation.targetType());

        } catch (Exception e) {
            // 日志记录失败不影响业务
            log.error("记录操作日志失败", e);
        }
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xReal = request.getHeader("X-Real-IP");
        if (xReal != null && !xReal.isEmpty()) {
            return xReal;
        }
        return request.getRemoteAddr();
    }
}
