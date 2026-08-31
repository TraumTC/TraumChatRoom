package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.OnlineUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多设备在线状态的上下线通知广播（P1-8 回归）
 *
 * 「X 上线了 / X 下线了」只能在状态真正跃迁时广播：
 * 开第二个设备不重复弹上线，关掉多设备中的一台不误弹下线。
 * 在线列表广播是幂等的，每次连接/断开都发以保证列表准确。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketChatControllerOnlineTest {

    private static final String NOTIFY_TOPIC = "/topic/private-notifications";
    private static final String ONLINE_TOPIC = "/topic/onlineUsers";

    @Mock
    private OnlineUserService onlineUserService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private WebSocketChatController controller;

    /** 在线列表取空集，避免走到 getDisplayName 的取名分支 */
    private void emptyOnlineList() {
        when(onlineUserService.getOnlineUsers()).thenReturn(Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    private void verifyNotified(String expectedType, int wantedTimes) {
        verify(messagingTemplate, times(wantedTimes)).convertAndSend(eq(NOTIFY_TOPIC),
                (Object) org.mockito.ArgumentMatchers.argThat(payload ->
                        payload instanceof Map<?, ?> m && expectedType.equals(m.get("type"))));
    }

    @Test
    void firstSessionBroadcastsOnlineNotice() {
        emptyOnlineList();
        when(onlineUserService.userOnline("alice", "s1")).thenReturn(true);

        controller.onUserConnect("alice", "s1");

        verifyNotified("user_online", 1);
        verify(messagingTemplate).convertAndSend(eq(ONLINE_TOPIC), any(Object.class));
    }

    @Test
    void secondDeviceDoesNotRebroadcastOnlineNotice() {
        emptyOnlineList();
        // 该用户已有其它设备在线 → 非首个会话
        when(onlineUserService.userOnline("alice", "s2")).thenReturn(false);

        controller.onUserConnect("alice", "s2");

        verifyNotified("user_online", 0);
        // P2-17：在线集合没变，列表也不再广播（新客户端自己走 /app/sync-state 取）
        verify(messagingTemplate, never()).convertAndSend(eq(ONLINE_TOPIC), any(Object.class));
    }

    @Test
    void closingOneOfSeveralDevicesDoesNotBroadcastOfflineNotice() {
        emptyOnlineList();
        // 还有别的设备在线 → 不是最后一个会话
        when(onlineUserService.userOffline("alice", "s1")).thenReturn(false);

        controller.onUserDisconnect("alice", "s1");

        verifyNotified("user_offline", 0);
        // P2-17：在线集合没变，列表也不再广播
        verify(messagingTemplate, never()).convertAndSend(eq(ONLINE_TOPIC), any(Object.class));
    }

    @Test
    void lastSessionBroadcastsOfflineNotice() {
        emptyOnlineList();
        when(onlineUserService.userOffline("alice", "s2")).thenReturn(true);

        controller.onUserDisconnect("alice", "s2");

        verifyNotified("user_offline", 1);
        verify(messagingTemplate).convertAndSend(eq(ONLINE_TOPIC), any(Object.class));
    }

    @Test
    void sessionIdIsForwardedToOnlineUserService() {
        emptyOnlineList();

        controller.onUserConnect("bob", "conn-42");
        controller.onUserDisconnect("bob", "conn-42");

        verify(onlineUserService).userOnline("bob", "conn-42");
        verify(onlineUserService).userOffline("bob", "conn-42");
    }

    /**
     * 完整的多设备时序：手机上线 → 电脑上线 → 关电脑 → 关手机。
     * 全程只应弹一次「上线了」和一次「下线了」，
     * 在线列表也只在这两次状态跃迁时广播（P2-17：中间两次集合没变，不发）。
     */
    @Test
    void twoDeviceLifecycleEmitsExactlyOneOnlineAndOneOfflineNotice() {
        emptyOnlineList();
        when(onlineUserService.userOnline("alice", "phone")).thenReturn(true);
        when(onlineUserService.userOnline("alice", "pc")).thenReturn(false);
        when(onlineUserService.userOffline("alice", "pc")).thenReturn(false);
        when(onlineUserService.userOffline("alice", "phone")).thenReturn(true);

        controller.onUserConnect("alice", "phone");
        controller.onUserConnect("alice", "pc");
        controller.onUserDisconnect("alice", "pc");
        controller.onUserDisconnect("alice", "phone");

        verifyNotified("user_online", 1);
        verifyNotified("user_offline", 1);
        // 4 次事件里只有首次上线与最后离线这 2 次改变了在线集合
        verify(messagingTemplate, times(2)).convertAndSend(eq(ONLINE_TOPIC), any(Object.class));
    }

    @Test
    void heartbeatCarriesSessionId() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("hb-1");

        controller.heartbeat(() -> "alice", accessor);

        verify(onlineUserService).updateHeartbeat("alice", "hb-1");
    }

    @Test
    void heartbeatWithoutPrincipalIsIgnored() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("hb-1");

        controller.heartbeat(null, accessor);

        verify(onlineUserService, never()).updateHeartbeat(any(), any());
    }
}
