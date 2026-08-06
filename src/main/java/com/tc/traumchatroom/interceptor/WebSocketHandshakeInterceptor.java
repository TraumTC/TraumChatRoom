package com.tc.traumchatroom.interceptor;

import com.tc.traumchatroom.util.JwtUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 在 WebSocket 连接建立前进行认证
 *
 * 认证方式：
 * 1. STOMP CONNECT header 中的 Authorization: Bearer xxx
 * 2. URL 参数 ?token=xxx（SockJS 降级方案）
 * 3. 无 token 时标记为游客
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 尝试从 URL 参数获取 token
        String token = extractTokenFromUrl(request.getURI());

        // 2. 如果 URL 没有 token，尝试从 header 获取
        if (!StringUtils.hasText(token)) {
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        // 3. 验证 token
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            attributes.put("username", username);
            attributes.put("authenticated", true);
            log.debug("WebSocket 握手成功，用户: {}", username);
        } else {
            // 无有效 token，标记为游客
            attributes.put("username", "guest_" + System.currentTimeMillis());
            attributes.put("authenticated", false);
            log.debug("WebSocket 握手成功，游客身份");
        }

        return true;  // 允许连接
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的回调（一般不需要处理）
    }

    /**
     * 从 URL 参数中提取 token
     * 格式：ws://localhost:8080/ws?token=eyJhbG...
     */
    private String extractTokenFromUrl(URI uri) {
        String query = uri.getQuery();
        if (StringUtils.hasText(query)) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "token".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }
}
