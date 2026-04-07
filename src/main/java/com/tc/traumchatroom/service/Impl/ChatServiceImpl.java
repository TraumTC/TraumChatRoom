package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private UserService userService;
    @Resource
    private MessageMapper messageMapper;
    @Override
    public Message sendChatMessage(String content, SimpMessageHeaderAccessor headerAccessor) {
        User currentUser = userService.getCurrentUser(headerAccessor);
        if (currentUser == null) {
            throw new RuntimeException("未找到当前登录用户，请重新登录");
        }
        Integer senderId = currentUser.getId();

        Message message = new Message(null,
                senderId,
                currentUser.getName(),
                "",
                content,
                LocalDateTime.now());
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
        User currentUser = userService.getCurrentUser(headerAccessor);
        if (currentUser == null) {
            throw new RuntimeException("未找到当前登录用户，请重新登录");
        }

        Integer senderId = currentUser.getId();

        Message message = new Message(null,
                senderId,
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
    @Override
    public void saveMessage(Message message){
        messageMapper.insertMessage(message);
    }


}
