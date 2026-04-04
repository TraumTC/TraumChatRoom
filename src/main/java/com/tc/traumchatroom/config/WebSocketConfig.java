package com.tc.traumchatroom.config;

import com.tc.traumchatroom.util.OnlineUserUtil;
import com.tc.traumchatroom.util.UserUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;

@Configuration
//开启信息代理
@EnableWebSocketMessageBroker
public class WebSocketConfig  implements WebSocketMessageBrokerConfigurer {
    @Resource
    private OnlineUserUtil onlineUserUtil;
    @Autowired
    private ApplicationContext applicationContext;

    private SimpMessagingTemplate messagingTemplate;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        if (authentication != null && authentication.isAuthenticated()
                                && !"anonymousUser".equals(authentication.getPrincipal())) {
                            attributes.put("authenticatedUser", authentication.getName());
                        }
                        return true;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .withSockJS()
        ;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)    // 核心线程 20（足够小型聊天）
                .maxPoolSize(50)     // 最大线程 50
                .queueCapacity(1000); // 队列缓冲

        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (messagingTemplate == null) {
                    messagingTemplate = applicationContext.getBean(SimpMessagingTemplate.class);
                }
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    String username = getUsernameFromSession(accessor);

                    if (username != null) {
                        if (StompCommand.CONNECT.equals(command)) {
                            onlineUserUtil.addUser(username);
                            Set<String> onlineUsers = onlineUserUtil.getOnlineUsers();
                            messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
                        } else if (StompCommand.DISCONNECT.equals(command)) {
                            onlineUserUtil.removeUser(username);
                            Set<String> onlineUsers = onlineUserUtil.getOnlineUsers();
                            messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
                        }
                    }
                }
                return message;
            }
            private String getUsernameFromSession(StompHeaderAccessor accessor) {
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null) {
                    return (String) sessionAttributes.get("authenticatedUser");
                }
                return null;
            }
            private void broadcastOnlineUsers() {
                Set<String> onlineUsers = onlineUserUtil.getOnlineUsers();
                messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
            }
        });
    }
}
