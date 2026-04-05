package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.util.HtmlEscapeUtil;
import com.tc.traumchatroom.util.UserUtil;
import jakarta.annotation.Resource;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private UserUtil userUtil;
    @Resource
    private HtmlEscapeUtil htmlEscapeUtil;
    @Resource
    private MessageMapper messageMapper;
    @Override
    public Message sendChatMessage(String content,SimpMessageHeaderAccessor headerAccessor) {
        User currentUser = null;
        currentUser = userUtil.getCurrentUser(headerAccessor);
        if (currentUser == null) {
            throw new RuntimeException("未找到当前登录用户，请重新登录");
        }
        String escapedContent = htmlEscapeUtil.escapeHtmlAndLinkify(content);
        Message message = new Message(null,
                                        currentUser.getId(),
                                        currentUser.getName(),
                                    "",
                                    content, LocalDateTime.now());
        messageMapper.addMessage(message);
        return message;
    }

    @Override
    public List<Message> getThreeDayMessages() {
        List<Message> messages = messageMapper.findByThreeDays();
        return messages;
    }

    @Override
    public Message sendPrivateMessage(String receiverName, String content, SimpMessageHeaderAccessor headerAccessor) {
        User currentUser = userUtil.getCurrentUser(headerAccessor);
        if (currentUser == null) {
            throw new RuntimeException("未找到当前登录用户，请重新登录");
        }
        String escapedContent = htmlEscapeUtil.escapeHtmlAndLinkify(content);
        Message message = new Message(null,
                currentUser.getId(),
                currentUser.getName(),
                receiverName,
                content,
                LocalDateTime.now());
        messageMapper.addMessage(message);
        return message;
    }
    @Override
    public List<Message> getPrivateMessageHistory(String name1, String name2) {
        List<Message> messages = messageMapper.findByPrivateThreeDays(name1, name2);
        return messages != null ? messages : new java.util.ArrayList<>();
    }

    public Message createSystemMessage(String message) {
        Message systemMsg = new Message();
        systemMsg.setSender("系统");
        systemMsg.setReceiver("");
        systemMsg.setMessage(message);
        systemMsg.setSendTime(LocalDateTime.now());
        return systemMsg;
    }
}
