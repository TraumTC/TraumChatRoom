package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.OnlineUserInfo;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.util.OnlineUserUtil;
import com.tc.traumchatroom.util.UserUtil;
import jakarta.annotation.Resource;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Controller

public class WebSocketChatController {

    @Resource
    private ChatService chatService;
    @Resource
    private OnlineUserUtil onlineUserUtil;
    @Resource
    private UserUtil userUtil;

    @MessageMapping("/ChatRoom")
    @SendTo("/topic/messages")
    public Message sendAllMessage(String content,SimpMessageHeaderAccessor headerAccessor ) {
        Message message = chatService.sendChatMessage(content,headerAccessor);
        return message;
    }

    @GetMapping("/history")
    @ResponseBody
    public List<Message> getThreeDayMessages() {
        List<Message> messages = chatService.getThreeDayMessages();
        if (messages == null){
            return new ArrayList<>();
        }
        return messages;
    }
    @GetMapping("/api/online-users")
    @ResponseBody
    public OnlineUserInfo getOnlineUsers() {
        Set<String> users = onlineUserUtil.getOnlineUsers();
        return new OnlineUserInfo(users.size(), users);
    }
}
