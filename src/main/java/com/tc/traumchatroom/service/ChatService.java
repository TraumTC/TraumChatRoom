package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.Message;

import java.security.Principal;

public interface ChatService {
    Message sendChatMessage(String content);
}
