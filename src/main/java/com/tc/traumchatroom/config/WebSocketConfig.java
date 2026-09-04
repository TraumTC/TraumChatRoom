package com.tc.traumchatroom.config;

import com.tc.traumchatroom.controller.WebSocketChatController;
import com.tc.traumchatroom.interceptor.WebSocketHandshakeInterceptor;
import com.tc.traumchatroom.util.JwtUtil;
import com.tc.traumchatroom.service.RefreshTokenStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
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
import java.util.Arrays;
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

    /**
     * WS 允许的来源，与 HTTP CORS 复用同一份白名单。
     *
     * 默认值与 {@code SecurityConfig} 保持一致 —— 两处各读一次同一属性属于既有的
     * 双份 CORS 配置问题（见审查报告 P3-28），这里不新引入第三套默认值。
     */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 凭据不合法时回给客户端的 ERROR 帧文案。
     *
     * 这条消息会发给任意匿名连接者，所以只放通用提示、不带任何内部细节；
     * {@code UNAUTHORIZED} 前缀供前端识别「是凭据问题、重连也没用」，
     * 据此主动终止 stompjs 默认的 5 秒一次无限重连（见 useWebSocket.js 的 onStompError）。
     *
     * 用 {@link MessageDeliveryException} 而非普通异常：它的 {@code getMessage()} 只返回
     * 这里的描述，被拒绝的原始帧仅出现在 {@code toString()} 中，
     * 因此不会把 CONNECT 帧的 Authorization 头回显给客户端。
     */
    static final String UNAUTHORIZED_MESSAGE = "UNAUTHORIZED: 需要有效登录凭据";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(resolveAllowedOrigins())
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
    }

    /**
     * 解析 WS 来源白名单。
     *
     * 原先是 {@code setAllowedOriginPatterns("*")}，任意第三方网页都能在访客浏览器里
     * 建立这条连接。改为读 {@code cors.allowed-origins}，与 HTTP 侧同一份配置。
     *
     * 仍用 {@code AllowedOriginPatterns} 而不是 {@code AllowedOrigins}：精确来源在两者下
     * 行为一致，但前者额外支持 {@code https://*.example.com} 这类写法，便于后续按需配置。
     *
     * 注意 origin 白名单只约束浏览器 —— 非浏览器客户端可以不发或伪造 Origin 头，
     * 真正封住匿名读取的是下面 {@link #wsChannelInterceptor()} 的 CONNECT 鉴权。
     *
     * 白名单解析为空时直接启动失败：此时端点会拒绝所有来源，与其在运行期表现为
     * 「谁都连不上」的费解故障，不如在启动阶段就把配置错误暴露出来。
     */
    private String[] resolveAllowedOrigins() {
        String[] origins = Arrays.stream((allowedOrigins == null ? "" : allowedOrigins).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            throw new IllegalStateException(
                    "cors.allowed-origins 解析为空，WebSocket 端点将拒绝所有来源，请检查 CORS_ALLOWED_ORIGINS 配置");
        }
        return origins;
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
     * 入站 Channel 拦截器：CONNECT 鉴权（解析 JWT → 设置 Principal），SUBSCRIBE 纵深防御。
     *
     * 原实现只在 CONNECT 时「解析得到用户名就设 Principal」，解析不到则什么都不做、
     * 照常放行。结果是不带 token 也能连上，虽然发不出消息（{@code @MessageMapping} 的
     * {@code Principal} 为 null 时静默 return），但可以订阅 {@code /topic/messages}
     * 拿到全部群聊实时流、{@code /topic/onlineUsers} 拿到在线名单。
     *
     * 现在改为解析不到身份就拒绝 CONNECT 帧：Spring 的 StompSubProtocolHandler 会把异常
     * 转成 ERROR 帧回给客户端并关闭连接（且对未发送成功的 CONNECT/SUBSCRIBE 主动跳过
     * ERROR 级日志，不会被匿名连接刷日志），匿名连接连建立都建立不起来。
     *
     * 游客不受影响：游客走 /api/auth/guest 拿到的是带 sid 的真 JWT 且在
     * RefreshTokenStore 里有会话记录，{@link #resolveUsername} 正常返回用户名。
     */
    @Bean
    public ChannelInterceptor wsChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                StompCommand command = accessor.getCommand();
                if (command == null) {
                    // 心跳帧（STOMP EOL）没有 command，直接放行
                    return message;
                }

                // CONNECT / STOMP：解析不出身份就拒绝建连（STOMP 1.2 客户端可能用 STOMP 帧代替 CONNECT，
                // Spring 对两者一视同仁，这里保持一致，避免留下「换个帧名就绕过」的口子）
                if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
                    String username = resolveUsername(accessor);
                    if (username == null) {
                        log.debug("拒绝无有效凭据的 WebSocket 连接 (session={})", accessor.getSessionId());
                        throw new MessageDeliveryException(message, UNAUTHORIZED_MESSAGE);
                    }
                    // 设置 Principal，后续 @MessageMapping 的 Principal 参数即可用；
                    // Spring 通过 CONNECT 帧上注册的 userChangeCallback 把它记在会话上，
                    // 之后同一会话的每个帧（含 SUBSCRIBE/SEND）都会带上它
                    accessor.setUser(() -> username);
                    // 带上 STOMP 会话 ID：同一账号多设备并行在线时用它做引用计数
                    webSocketChatController.onUserConnect(username, accessor.getSessionId());
                    log.info("用户连接 WebSocket: {} (session={})", username, accessor.getSessionId());
                    return message;
                }

                // SUBSCRIBE：纵深防御。CONNECT 已经拦住匿名会话，正常情况下这里不会触发；
                // 留着是为了「就算将来有别的路径建出了无身份会话，也订阅不到任何东西」。
                // SEND 不在此处拦：它落到 @MessageMapping 时本来就因 Principal 为 null 而被丢弃，
                // 而 Spring 只对 CONNECT/SUBSCRIBE 跳过 ERROR 级日志，拦 SEND 会多出一条可被刷的日志。
                if (StompCommand.SUBSCRIBE.equals(command) && accessor.getUser() == null) {
                    log.debug("拒绝无身份会话的订阅: destination={} (session={})",
                            accessor.getDestination(), accessor.getSessionId());
                    throw new MessageDeliveryException(message, UNAUTHORIZED_MESSAGE);
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
