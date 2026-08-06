package com.tc.traumchatroom.config;

import com.tc.traumchatroom.controller.WebSocketChatController;
import com.tc.traumchatroom.interceptor.WebSocketHandshakeInterceptor;
import com.tc.traumchatroom.util.JwtUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.security.Principal;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * WebSocket 配置
 *
 * STOMP 协议配置：
 * - 端点：/ws（SockJS 降级）
 * - 消息代理：/topic（广播）、/queue（点对点）
 * - 应用前缀：/app
 * - 用户前缀：/user
 * - 心跳：10秒
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Resource
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    @Lazy
    @Resource
    private WebSocketChatController webSocketChatController;

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("ws-inbound-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        registration.taskExecutor(executor);

        // 添加 Channel 拦截器，处理 CONNECT 事件（从 STOMP header 提取 JWT）
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String username = resolveUsername(accessor);
                    if (username != null) {
                        // 设置 Principal，后续 @MessageMapping 的 Principal 参数即可用
                        accessor.setUser(() -> username);
                        webSocketChatController.onUserConnect(username);
                        log.info("用户连接 WebSocket: {}", username);
                    }
                }
                return message;
            }

            /**
             * 从 STOMP CONNECT 帧解析用户名
             * 优先级：
             * 1. STOMP header Authorization: Bearer xxx（前端 connectHeaders 发送，最可靠）
             * 2. 握手拦截器放入的 session attributes（URL token 兜底）
             */
            private String resolveUsername(StompHeaderAccessor accessor) {
                // 方式一：STOMP header Authorization
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (jwtUtil.validateToken(token)) {
                        return jwtUtil.getUsernameFromToken(token);
                    }
                }

                // 方式二：握手拦截器从 URL 提取的 username
                Object attrUsername = accessor.getSessionAttributes() != null
                        ? accessor.getSessionAttributes().get("username")
                        : null;
                Object attrAuth = accessor.getSessionAttributes() != null
                        ? accessor.getSessionAttributes().get("authenticated")
                        : null;
                if (attrUsername != null && Boolean.TRUE.equals(attrAuth)) {
                    return (String) attrUsername;
                }

                return null;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(128 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(20 * 1000);
    }

    /**
     * 监听 WebSocket 连接事件
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        log.debug("WebSocket 会话连接: {}", event.getUser());
    }

    /**
     * 监听 WebSocket 断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            String username = user.getName();
            webSocketChatController.onUserDisconnect(username);
            log.info("用户断开 WebSocket: {}", username);
        }
    }
}
