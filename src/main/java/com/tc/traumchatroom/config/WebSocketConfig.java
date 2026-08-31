package com.tc.traumchatroom.config;

import com.tc.traumchatroom.controller.WebSocketChatController;
import com.tc.traumchatroom.interceptor.WebSocketHandshakeInterceptor;
import com.tc.traumchatroom.util.JwtUtil;
import com.tc.traumchatroom.service.RefreshTokenStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
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

    @Resource
    private RefreshTokenStore refreshTokenStore;

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
        registration.taskExecutor(wsInboundExecutor());
        registration.interceptors(wsChannelInterceptor());
    }

    /**
     * WebSocket 入站线程池（由容器管理生命周期，关闭时自动回收）
     */
    @Bean
    public ThreadPoolTaskExecutor wsInboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("ws-inbound-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    /**
     * 入站 Channel 拦截器：处理 CONNECT 事件（从 STOMP header 提取 JWT）
     */
    @Bean
    public ChannelInterceptor wsChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String username = resolveUsername(accessor);
                    if (username != null) {
                        // 设置 Principal，后续 @MessageMapping 的 Principal 参数即可用
                        accessor.setUser(() -> username);
                        // 带上 STOMP 会话 ID：同一账号多设备并行在线时用它做引用计数
                        webSocketChatController.onUserConnect(username, accessor.getSessionId());
                        log.info("用户连接 WebSocket: {} (session={})", username, accessor.getSessionId());
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
                    if (jwtUtil.validateAccessToken(token)) {
                        String username = jwtUtil.getUsernameFromToken(token);
                        String sessionId = jwtUtil.getSessionIdFromToken(token);
                        if (refreshTokenStore.isSessionActive(username, sessionId)) return username;
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
        };
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
     *
     * 只注销当前这一个会话；该用户是否真的离线由 OnlineUserService 按存活会话数判定，
     * 多设备场景下关掉任意一台不会把整个账号判离线。
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            String username = user.getName();
            webSocketChatController.onUserDisconnect(username, event.getSessionId());
            log.info("用户断开 WebSocket: {} (session={})", username, event.getSessionId());
        } else {
            // 无用户上下文：通常是握手未完成/连接建立前异常断开，生产 WARN 记录便于排查
            log.warn("WebSocket 断开但无用户上下文（可能为异常断连或握手未完成）");
        }
    }
}
