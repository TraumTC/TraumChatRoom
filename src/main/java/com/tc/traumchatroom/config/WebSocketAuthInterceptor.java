package com.tc.traumchatroom.config;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.UserDetailsService;
import com.tc.traumchatroom.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (!StringUtils.hasText(token)) {
                String bearer = servletRequest.getServletRequest().getHeader("Authorization");
                if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                    token = bearer.substring(7);
                }
            }

            if (StringUtils.hasText(token) && jwtUtil.validateToken(token) && !jwtUtil.isTokenExpired(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                String name = jwtUtil.getNameFromToken(token);
                if (username != null) {
                    attributes.put("authenticatedUser", username);
                    attributes.put("authenticatedUserName", name);
                    return true;
                }
            }

            // 无 token 时回退到 Session 获取游客或登录用户
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session != null) {
                User currentUser = (User) session.getAttribute("CURRENT_USER");
                if (currentUser != null) {
                    attributes.put("authenticatedUser", currentUser.getUsername());
                    attributes.put("authenticatedUserName", currentUser.getName());
                    return true;
                }
                User guestUser = (User) session.getAttribute("GUEST_USER");
                if (guestUser != null) {
                    attributes.put("authenticatedUser", guestUser.getUsername());
                    attributes.put("authenticatedUserName", guestUser.getName());
                    return true;
                }
            }

            // 兜底：生成临时游客
            String guestName = "guest_" + System.currentTimeMillis();
            attributes.put("authenticatedUser", guestName);
            attributes.put("authenticatedUserName", "游客");
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
