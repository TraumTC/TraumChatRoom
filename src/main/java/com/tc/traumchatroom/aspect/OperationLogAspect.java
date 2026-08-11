package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.entity.OperationLog;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.OperationLogMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    /** detail 中禁止记录的敏感字段 */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldPassword", "newPassword", "confirmPassword",
            "refreshToken", "accessToken", "token", "secret", "jwt"
    );

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private HttpServletRequest request;

    /**
     * 方法执行成功后记录日志
     */
    @AfterReturning(pointcut = "@annotation(com.tc.traumchatroom.annotation.LogOperation)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        recordLog(joinPoint, true, null);
    }

    /**
     * 方法执行异常后记录日志（用于记录登录失败等场景）
     */
    @AfterThrowing(pointcut = "@annotation(com.tc.traumchatroom.annotation.LogOperation)", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Throwable ex) {
        recordLog(joinPoint, false, ex.getMessage());
    }

    /**
     * 统一记录操作日志
     */
    private void recordLog(JoinPoint joinPoint, boolean success, String errorMsg) {
        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            LogOperation logAnnotation = method.getAnnotation(LogOperation.class);

            if (logAnnotation == null) return;

            // 获取当前用户（登录/注册等未认证场景会回退为 anonymous）
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "anonymous";

            // 未认证场景：尝试从请求参数中提取用户名（登录/注册）
            Object[] args = joinPoint.getArgs();
            if ("anonymous".equals(username) || username == null) {
                String paramUsername = extractUsernameFromArgs(args);
                if (paramUsername != null) {
                    username = paramUsername;
                }
            }

            // 构造操作日志
            OperationLog operationLog = new OperationLog();
            operationLog.setUsername(username);
            operationLog.setAction(logAnnotation.action());
            operationLog.setTargetType(logAnnotation.targetType());

            // 填充 userId（通过用户名查找）
            try {
                if (username != null && !"anonymous".equals(username)) {
                    User user = userMapper.findByUsername(username);
                    if (user != null) {
                        operationLog.setUserId(user.getId());
                    }
                }
            } catch (Exception e) {
                log.debug("查找用户ID失败: {}", username);
            }

            // 获取目标ID（从方法参数中提取 Long/Integer 类型的参数）
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    if (arg instanceof Long || arg instanceof Integer) {
                        operationLog.setTargetId(((Number) arg).longValue());
                        break;
                    }
                }
            }

            // 构建 detail 字段（记录关键参数信息，排除敏感字段）
            String detail = buildDetail(args, success, errorMsg);
            operationLog.setDetail(detail);

            // 获取客户端 IP
            operationLog.setIp(getClientIp());

            // 获取 User-Agent
            String userAgent = request.getHeader("User-Agent");
            operationLog.setUserAgent(userAgent != null ? userAgent.substring(0, Math.min(500, userAgent.length())) : null);

            // 保存到数据库
            operationLogMapper.insert(operationLog);

            log.debug("操作日志: {} - {} - {} - success={}", username, logAnnotation.action(), logAnnotation.targetType(), success);

        } catch (Exception e) {
            // 日志记录失败不影响业务
            log.error("记录操作日志失败", e);
        }
    }

    /**
     * 从未认证场景的方法参数中提取用户名
     */
    private String extractUsernameFromArgs(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof LoginRequest loginRequest) {
                return loginRequest.getUsername();
            }
            if (arg instanceof RegisterRequest registerRequest) {
                return registerRequest.getUsername();
            }
        }
        return null;
    }

    /**
     * 构建操作详情（JSON 格式）
     * 记录关键参数，但排除敏感信息（密码、token 等）
     */
    private String buildDetail(Object[] args, boolean success, String errorMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":").append(success);

        if (args != null && args.length > 0) {
            sb.append(",\"params\":[");
            boolean first = true;
            for (Object arg : args) {
                if (!first) sb.append(",");
                first = false;
                sb.append(paramToJsonSafe(arg));
            }
            sb.append("]");
        }

        if (errorMsg != null) {
            String msg = errorMsg.length() > 300 ? errorMsg.substring(0, 300) + "..." : errorMsg;
            msg = msg.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append(",\"error\":\"").append(msg).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 将单个方法参数转为安全的 JSON 片段（排除敏感信息）
     */
    private String paramToJsonSafe(Object arg) {
        if (arg == null) return "null";

        // HTTP 请求对象无法序列化
        if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) {
            return "\"<HttpServletRequest>\"";
        }

        // 登录请求：只保留用户名
        if (arg instanceof LoginRequest loginRequest) {
            return "{\"username\":\"" + jsonEscape(loginRequest.getUsername()) + "\"}";
        }

        // 注册请求：只保留用户名和昵称
        if (arg instanceof RegisterRequest registerRequest) {
            return "{\"username\":\"" + jsonEscape(registerRequest.getUsername())
                    + "\",\"name\":\"" + jsonEscape(registerRequest.getName()) + "\"}";
        }

        // Map 参数：排除敏感 key
        if (arg instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (SENSITIVE_KEYS.contains(key)) continue;
                if (!first) sb.append(",");
                first = false;
                String value = entry.getValue() == null ? "" : jsonEscape(String.valueOf(entry.getValue()));
                sb.append("\"").append(jsonEscape(key)).append("\":\"").append(value).append("\"");
            }
            sb.append("}");
            return sb.toString();
        }

        // 其他对象：安全 toString
        String str = arg.toString();
        if (str.length() > 200) {
            str = str.substring(0, 200) + "...";
        }
        return "\"" + jsonEscape(str) + "\"";
    }

    /**
     * JSON 字符串转义
     */
    private String jsonEscape(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
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
