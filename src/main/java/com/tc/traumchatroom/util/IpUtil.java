package com.tc.traumchatroom.util;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具
 * 优先取 X-Forwarded-For 首项（支持反向代理），回退到直接连接地址
 */
public final class IpUtil {

    private IpUtil() {
    }

    /**
     * 从 HTTP 请求解析客户端 IP
     */
    public static String fromHttp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) return first;
        }
        return request.getRemoteAddr();
    }

    /**
     * 从 WebSocket 握手请求解析客户端 IP
     */
    public static String fromWebSocket(ServerHttpRequest request) {
        if (request == null) return null;
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) return first;
        }
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : null;
    }
}
