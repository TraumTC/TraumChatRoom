package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;

public interface ChatService {
    Message sendChatMessage( String content,SimpMessageHeaderAccessor headerAccessor);
    List<Message> getThreeDayMessages();
    Message sendPrivateMessage(String receiverName, String content, SimpMessageHeaderAccessor headerAccessor);
    List<Message> getPrivateMessageHistory(String name1, String name2);
}
