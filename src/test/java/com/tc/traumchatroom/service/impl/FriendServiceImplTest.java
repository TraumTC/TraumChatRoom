package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.dto.request.FriendApplyRequest;
import com.tc.traumchatroom.dto.request.FriendHandleRequest;
import com.tc.traumchatroom.entity.FriendRequest;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.FriendMapper;
import com.tc.traumchatroom.mapper.FriendRequestMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.OnlineUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FriendServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    @Mock
    private FriendMapper friendMapper;
    @Mock
    private FriendRequestMapper friendRequestMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private OnlineUserService onlineUserService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private FriendServiceImpl friendService;

    private User user(Integer id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setName(username);
        return u;
    }

    // ---------- 发送好友申请 ----------

    @Test
    void sendRequestCannotAddSelf() {
        User sender = user(1, "a");
        when(userMapper.findByUsername("a")).thenReturn(sender);
        when(userMapper.findById(1)).thenReturn(sender); // receiver 就是自己

        FriendApplyRequest req = new FriendApplyRequest();
        req.setReceiverId(1);

        assertThatThrownBy(() -> friendService.sendRequest("a", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_ADD_SELF);
    }

    @Test
    void sendRequestAlreadyFriends() {
        User sender = user(1, "a");
        User receiver = user(2, "b");
        when(userMapper.findByUsername("a")).thenReturn(sender);
        when(userMapper.findById(2)).thenReturn(receiver);
        when(friendMapper.exists(1, 2)).thenReturn(true);

        FriendApplyRequest req = new FriendApplyRequest();
        req.setReceiverId(2);

        assertThatThrownBy(() -> friendService.sendRequest("a", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_FRIENDS);
    }

    @Test
    void sendRequestDuplicatePending() {
        User sender = user(1, "a");
        User receiver = user(2, "b");
        when(userMapper.findByUsername("a")).thenReturn(sender);
        when(userMapper.findById(2)).thenReturn(receiver);
        when(friendMapper.exists(1, 2)).thenReturn(false);
        when(friendRequestMapper.findPendingBySenderAndReceiver(1, 2)).thenReturn(new FriendRequest());

        FriendApplyRequest req = new FriendApplyRequest();
        req.setReceiverId(2);

        assertThatThrownBy(() -> friendService.sendRequest("a", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUEST_EXISTS);
    }

    // ---------- 处理申请（同意 → 双向插入 + 清缓存 + 通知） ----------

    @Test
    void handleRequestAcceptCreatesBidirectionalFriendshipAndEvictsCache() {
        User current = user(2, "b");           // 接收者
        User sender = user(1, "a");
        FriendRequest fr = new FriendRequest();
        fr.setId(10L);
        fr.setSenderId(1);
        fr.setReceiverId(2);

        when(userMapper.findByUsername("b")).thenReturn(current);
        when(friendRequestMapper.findById(10L)).thenReturn(fr);
        // WebSocket 通知需要双方用户信息
        when(userMapper.findById(1)).thenReturn(sender);
        when(userMapper.findById(2)).thenReturn(current);

        FriendHandleRequest req = new FriendHandleRequest();
        req.setAction("accept");
        friendService.handleRequest(10L, "b", req);

        // 状态更新 + 双向两条插入
        verify(friendRequestMapper).updateStatus(10L, 1);
        verify(friendMapper, times(2)).insert(any());
        // 双方好友缓存失效
        verify(redisTemplate).delete("chat:friends:1");
        verify(redisTemplate).delete("chat:friends:2");
        // 通知申请方
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void handleRequestCannotHandleOthersRequest() {
        User current = user(3, "c");           // 不是接收者
        FriendRequest fr = new FriendRequest();
        fr.setId(10L);
        fr.setSenderId(1);
        fr.setReceiverId(2);

        when(userMapper.findByUsername("c")).thenReturn(current);
        when(friendRequestMapper.findById(10L)).thenReturn(fr);

        FriendHandleRequest req = new FriendHandleRequest();
        req.setAction("accept");

        assertThatThrownBy(() -> friendService.handleRequest(10L, "c", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(friendRequestMapper, never()).updateStatus(any(), any());
    }

    // ---------- 删除好友（双向 + 缓存失效） ----------

    @Test
    void deleteFriendRemovesBothDirectionsAndEvictsCache() {
        User current = user(1, "a");
        when(userMapper.findByUsername("a")).thenReturn(current);

        friendService.deleteFriend("a", 2);

        verify(friendMapper).delete(1, 2);
        verify(redisTemplate).delete("chat:friends:1");
        verify(redisTemplate).delete("chat:friends:2");
    }
}
