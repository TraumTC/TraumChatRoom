package com.tc.traumchatroom.config;

import com.tc.traumchatroom.controller.WebSocketChatController;
import com.tc.traumchatroom.service.RefreshTokenStore;
import com.tc.traumchatroom.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 入站帧鉴权（P0-2 回归）
 *
 * 原实现只在 CONNECT 解析得到用户名时设置 Principal，解析不到则静默放行 ——
 * 不带 token 也能连上并订阅 /topic/messages 拿到全部群聊实时流。
 *
 * 这组测试锁定两件事：
 * 1. 匿名（无 token / token 无效 / 会话已撤销）的 CONNECT 必须被拒；
 * 2. 游客与正常用户不能被误伤 —— 游客走 /api/auth/guest 拿到的是带 sid 的真 JWT，
 *    在 RefreshTokenStore 里有会话记录，必须照常放行。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketConfigChannelInterceptorTest {

    /** preSend 不关心下游 channel，给个恒成功的实现即可（send(Message,long) 是唯一抽象方法） */
    private static final MessageChannel CHANNEL = (m, timeout) -> true;

    private static final String TOKEN = "valid.access.token";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String SID = "session-abc";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private WebSocketChatController chatController;

    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketConfig config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(config, "refreshTokenStore", refreshTokenStore);
        ReflectionTestUtils.setField(config, "webSocketChatController", chatController);
        interceptor = config.wsChannelInterceptor();
    }

    // ---------- CONNECT：拒绝匿名 ----------

    @Test
    void connectWithoutAnyCredentialIsRejected() {
        Message<byte[]> connect = frame(StompCommand.CONNECT).build();

        assertThatThrownBy(() -> interceptor.preSend(connect, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage(WebSocketConfig.UNAUTHORIZED_MESSAGE);

        verify(chatController, never()).onUserConnect(any(), any());
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtUtil.validateAccessToken(TOKEN)).thenReturn(false);

        Message<byte[]> connect = frame(StompCommand.CONNECT).auth(BEARER).build();

        assertThatThrownBy(() -> interceptor.preSend(connect, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class);
        verify(chatController, never()).onUserConnect(any(), any());
    }

    @Test
    void connectWithRevokedSessionIsRejected() {
        // token 本身没过期，但会话已被 revokeAll 撤销（改密码/禁用/登出）
        stubToken("alice", SID);
        when(refreshTokenStore.isSessionActive("alice", SID)).thenReturn(false);

        Message<byte[]> connect = frame(StompCommand.CONNECT).auth(BEARER).build();

        assertThatThrownBy(() -> interceptor.preSend(connect, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class);
        verify(chatController, never()).onUserConnect(any(), any());
    }

    @Test
    void connectWithUnauthenticatedHandshakeAttributesIsRejected() {
        // 握手拦截器对无 token 的连接会塞入 guest_<millis> + authenticated=false，
        // 这类伪身份不得被当成合法用户
        Message<byte[]> connect = frame(StompCommand.CONNECT)
                .attributes(Map.of("username", "guest_1", "authenticated", false))
                .build();

        assertThatThrownBy(() -> interceptor.preSend(connect, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class);
        verify(chatController, never()).onUserConnect(any(), any());
    }

    @Test
    void stompFrameIsGuardedLikeConnect() {
        // STOMP 1.2 客户端可用 STOMP 帧代替 CONNECT，不能留成绕过口子
        Message<byte[]> connect = frame(StompCommand.STOMP).build();

        assertThatThrownBy(() -> interceptor.preSend(connect, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ---------- CONNECT：合法身份照常放行 ----------

    @Test
    void connectWithValidTokenSetsPrincipal() {
        stubToken("alice", SID);
        when(refreshTokenStore.isSessionActive("alice", SID)).thenReturn(true);

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT).auth(BEARER).build(), CHANNEL);

        assertThat(principalOf(result)).isNotNull();
        assertThat(principalOf(result).getName()).isEqualTo("alice");
        verify(chatController).onUserConnect("alice", SID);
    }

    @Test
    void guestWithValidTokenIsAccepted() {
        // 游客的核心保障：loginAsGuest 发的是带 sid 的真 JWT 且 refreshTokenStore.save 落了会话
        stubToken("guest_a1b2c3", SID);
        when(refreshTokenStore.isSessionActive("guest_a1b2c3", SID)).thenReturn(true);

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT).auth(BEARER).build(), CHANNEL);

        assertThat(principalOf(result)).isNotNull();
        assertThat(principalOf(result).getName()).isEqualTo("guest_a1b2c3");
        verify(chatController).onUserConnect("guest_a1b2c3", SID);
    }

    @Test
    void connectFallsBackToAuthenticatedHandshakeAttributes() {
        // SockJS 降级路径：token 从 URL 参数来，由握手拦截器校验后写入 session attributes
        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT)
                .attributes(Map.of("username", "bob", "authenticated", true))
                .build(), CHANNEL);

        assertThat(principalOf(result)).isNotNull();
        assertThat(principalOf(result).getName()).isEqualTo("bob");
        verify(chatController).onUserConnect("bob", SID);
    }

    // ---------- SUBSCRIBE：纵深防御 ----------

    @Test
    void subscribeWithoutPrincipalIsRejected() {
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE).destination("/topic/messages").build();

        assertThatThrownBy(() -> interceptor.preSend(subscribe, CHANNEL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage(WebSocketConfig.UNAUTHORIZED_MESSAGE);
    }

    @Test
    void subscribeWithPrincipalIsAllowed() {
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE)
                .destination("/topic/messages")
                .user("alice")
                .build();

        assertThat(interceptor.preSend(subscribe, CHANNEL)).isSameAs(subscribe);
    }

    // ---------- 不该被误拦的帧 ----------

    @Test
    void heartbeatFrameWithoutCommandPassesThrough() {
        // STOMP 心跳帧（EOL）没有 command。误拦这里会打断所有已连接会话
        StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
        accessor.setSessionId(SID);
        accessor.setLeaveMutable(true);
        Message<byte[]> heartbeat = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(accessor.getCommand()).isNull();
        assertThatCode(() -> interceptor.preSend(heartbeat, CHANNEL)).doesNotThrowAnyException();
    }

    @Test
    void sendIsNotGuardedHere() {
        // 刻意不拦 SEND：Principal 为 null 时 @MessageMapping 本就丢弃该消息，
        // 而 Spring 只对未发送成功的 CONNECT/SUBSCRIBE 跳过 ERROR 级日志，
        // 拦 SEND 会多出一条可被匿名连接刷屏的错误日志。
        Message<byte[]> send = frame(StompCommand.SEND).destination("/app/space").build();

        assertThatCode(() -> interceptor.preSend(send, CHANNEL)).doesNotThrowAnyException();
    }

    @Test
    void disconnectIsNotGuardedHere() {
        Message<byte[]> disconnect = frame(StompCommand.DISCONNECT).build();

        assertThatCode(() -> interceptor.preSend(disconnect, CHANNEL)).doesNotThrowAnyException();
    }

    // ---------- 辅助 ----------

    private void stubToken(String username, String sessionId) {
        when(jwtUtil.validateAccessToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(username);
        when(jwtUtil.getSessionIdFromToken(TOKEN)).thenReturn(sessionId);
    }

    private static Principal principalOf(Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        return accessor == null ? null : accessor.getUser();
    }

    private static FrameBuilder frame(StompCommand command) {
        return new FrameBuilder(command);
    }

    /**
     * STOMP 帧构造器。
     * {@code setLeaveMutable(true)} 是必需的：否则 getMessageHeaders() 之后 accessor 变为不可变，
     * 拦截器里的 setUser() 会抛异常，也拿不回同一个 accessor 实例来断言 Principal。
     * 生产环境由 Spring 自动注册的 ImmutableMessageChannelInterceptor 保证了同样的可变性。
     */
    private static final class FrameBuilder {
        private final StompHeaderAccessor accessor;

        private FrameBuilder(StompCommand command) {
            this.accessor = StompHeaderAccessor.create(command);
            this.accessor.setSessionId(SID);
        }

        FrameBuilder auth(String header) {
            accessor.setNativeHeader("Authorization", header);
            return this;
        }

        FrameBuilder attributes(Map<String, Object> attributes) {
            accessor.setSessionAttributes(attributes);
            return this;
        }

        FrameBuilder destination(String destination) {
            accessor.setDestination(destination);
            return this;
        }

        FrameBuilder user(String username) {
            accessor.setUser(() -> username);
            return this;
        }

        Message<byte[]> build() {
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
    }
}
