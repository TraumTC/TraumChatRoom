package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;

import java.time.LocalDateTime;


public class ChatServiceImpl implements ChatService {

    @Resource
    private UserService userService;
    @Override
    public Message sendChatMessage(String content) {
        User currentUser = userService.getCurrentUser();

        Message message = new Message(null,
                                        currentUser.getId(),
                                        currentUser.getUsername(),
                                    "",
                                    content, LocalDateTime.now());

        return message;
    }
}
