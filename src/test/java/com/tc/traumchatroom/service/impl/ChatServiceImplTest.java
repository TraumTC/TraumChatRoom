package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatServiceImpl 单元测试（消息撤回的业务规则）
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private MessageMapper messageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private User user(Integer id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private Message message(Long id, Integer senderId, String senderName, LocalDateTime createdAt) {
        Message m = new Message();
        m.setId(id);
        m.setSenderId(senderId);
        m.setSenderName(senderName);
        m.setCreatedAt(createdAt);
        m.setIsRecalled(0);
        m.setContent("原始内容");
        return m;
    }

    @BeforeEach
    void setUp() {
        // 默认当前用户（lenient：部分测试方法不触发该 stub）
        org.mockito.Mockito.lenient()
                .when(userMapper.findByUsername("a")).thenReturn(user(1, "a"));
    }

    @Test
    void ownerCanRecallWithinTwoMinutes() {
        when(messageMapper.findById(1L)).thenReturn(message(1L, 1, "a", LocalDateTime.now().minusMinutes(1)));

        chatService.recallMessage(1L, "a", "ROLE_USER");

        verify(messageMapper).updateRecall(1L, "a 撤回了一条消息", "原始内容");
    }

    @Test
    void recallTimeoutAfterTwoMinutes() {
        when(messageMapper.findById(1L)).thenReturn(message(1L, 1, "a", LocalDateTime.now().minusMinutes(3)));

        assertThatThrownBy(() -> chatService.recallMessage(1L, "a", "ROLE_USER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECALL_TIMEOUT);
    }

    @Test
    void nonOwnerCannotRecall() {
        // 消息属于 b，当前用户 a
        when(messageMapper.findById(1L)).thenReturn(message(1L, 2, "b", LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> chatService.recallMessage(1L, "a", "ROLE_USER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void adminCanRecallOthersWithinTwoMinutes() {
        // 管理员 a 撤回 b 的消息（2 分钟内）
        when(messageMapper.findById(1L)).thenReturn(message(1L, 2, "b", LocalDateTime.now().minusMinutes(1)));

        chatService.recallMessage(1L, "a", "ROLE_ADMIN");

        verify(messageMapper).updateRecall(1L, "b 撤回了一条消息", "原始内容");
    }

    @Test
    void alreadyRecalledMessageCannotRecallAgain() {
        Message m = message(1L, 1, "a", LocalDateTime.now().minusMinutes(1));
        m.setIsRecalled(1);
        when(messageMapper.findById(1L)).thenReturn(m);

        assertThatThrownBy(() -> chatService.recallMessage(1L, "a", "ROLE_USER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void missingMessageThrowsNotFound() {
        when(messageMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.recallMessage(999L, "a", "ROLE_USER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
