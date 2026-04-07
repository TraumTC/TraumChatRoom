package com.tc.traumchatroom.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendUserNotification(String sender, String type, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("sender", sender);
            notification.put("message", message);
            notification.put("sendTime", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/private-notifications", (Object) notification);
        } catch (Exception e) {
            throw new RuntimeException("发送通知失败", e);
        }
    }
}
