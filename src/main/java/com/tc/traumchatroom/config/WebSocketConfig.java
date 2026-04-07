package com.tc.traumchatroom.config;

import com.tc.traumchatroom.service.OnlineUserService;
import com.tc.traumchatroom.service.WebSocketSessionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Resource
    private WebSocketSessionService webSocketSessionService;

    @Resource
    private OnlineUserService onlineUserService;

    @Autowired
    private ApplicationContext applicationContext;

    private SimpMessagingTemplate messagingTemplate;

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("wss-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler());
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
                        String username = null;
                        String name = null;

                        if (request instanceof ServletServerHttpRequest) {
                            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;

                            String clientIp = getClientIpAddress(servletRequest);
                            attributes.put("ipAddress", clientIp);

                            String authParam = servletRequest.getServletRequest().getParameter("authenticated");
                            if (authParam != null && !authParam.isEmpty()) {
                                username = authParam;
                            }
                        }

                        if (username == null) {
                            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null && authentication.isAuthenticated()
                                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                                username = authentication.getName();
                            }
                        }

                        if (username == null) {
                            if (request instanceof ServletServerHttpRequest) {
                                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                                HttpSession session = servletRequest.getServletRequest().getSession(false);
                                if (session != null) {
                                    com.tc.traumchatroom.entity.User guestUser =
                                            (com.tc.traumchatroom.entity.User) session.getAttribute("GUEST_USER");
                                    if (guestUser != null) {
                                        username = guestUser.getUsername();
                                        name = guestUser.getName();
                                    }
                                }
                            }
                        }

                        if (username != null) {
                            attributes.put("authenticatedUser", username);
                            if (name != null) {
                                attributes.put("authenticatedUserName", name);
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }

                    private String getClientIpAddress(ServletServerHttpRequest request) {
                        String ip = request.getServletRequest().getHeader("X-Forwarded-For");
                        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                            ip = request.getServletRequest().getHeader("X-Real-IP");
                        }
                        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                            ip = request.getServletRequest().getHeader("Proxy-Client-IP");
                        }
                        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                            ip = request.getServletRequest().getHeader("WL-Proxy-Client-IP");
                        }
                        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                            ip = request.getServletRequest().getRemoteAddr();
                        }

                        if (ip != null && ip.contains(",")) {
                            ip = ip.split(",")[0].trim();
                        }

                        return ip != null ? ip : "unknown";
                    }
                })
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(50)
                .queueCapacity(1000);

        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (messagingTemplate == null) {
                    messagingTemplate = applicationContext.getBean(SimpMessagingTemplate.class);
                }

                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    String username = (String) accessor.getSessionAttributes().get("authenticatedUser");
                    String name = (String) accessor.getSessionAttributes().get("authenticatedUserName");

                    if (username != null) {
                        if (StompCommand.CONNECT.equals(command)) {
                            webSocketSessionService.handleUserConnected(username, name);
                            handleUserOnlineNotification(username, name);
                        } else if (StompCommand.DISCONNECT.equals(command)) {
                            String displayName = onlineUserService.getNameByUsername(username);
                            webSocketSessionService.handleUserDisconnected(username);
                            if (displayName != null) {
                                handleUserOfflineNotification(displayName);
                            }
                        } else if (StompCommand.SEND.equals(command)) {
                            String destination = accessor.getDestination();
                            if (destination != null && destination.contains("/heartbeat")) {
                                webSocketSessionService.handleHeartbeat(username);
                            }
                        }
                    }
                }
                return message;
            }

            private void handleUserOnlineNotification(String username, String name) {
                try {
                    String displayName = name != null ? name : username;
                    broadcastOnlineUsers();

                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "user_online");
                    notification.put("sender", displayName);
                    notification.put("message", displayName + " 已上线");
                    notification.put("sendTime", LocalDateTime.now().toString());

                    messagingTemplate.convertAndSend("/topic/private-notifications", (Object) notification);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            private void handleUserOfflineNotification(String name) {
                try {
                    broadcastOnlineUsers();

                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "user_offline");
                    notification.put("sender", name);
                    notification.put("message", name + " 已下线");
                    notification.put("sendTime", LocalDateTime.now().toString());

                    messagingTemplate.convertAndSend("/topic/private-notifications", (Object) notification);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            private void broadcastOnlineUsers() {
                Set<String> onlineUsers = onlineUserService.getOnlineUsers();
                messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
            }
        });
    }
}

