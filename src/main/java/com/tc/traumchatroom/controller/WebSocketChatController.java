package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller

public class WebSocketChatController {

    @Resource
    private ChatService chatService;

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

}
