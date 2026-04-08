package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.OnlineUserInfo;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.service.OnlineUserService;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller

public class WebSocketChatController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChatController.class);

    @Resource
    private ChatService chatService;
    @Resource
    private OnlineUserService onlineUserService;
    @Resource
    private UserService userService;
    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/ChatRoom")
    @SendTo("/topic/messages")
    public Message sendAllMessage(String content,SimpMessageHeaderAccessor headerAccessor ) {
        Message message = chatService.sendChatMessage(content,headerAccessor);
        return message;
    }

    @MessageMapping("/heartbeat")
    public void handleHeartbeat(SimpMessageHeaderAccessor headerAccessor) {
        String username = getUsernameFromSession(headerAccessor);
        if (username != null) {
            onlineUserService.updateHeartbeat(username);
            log.debug("收到用户 {} 的心跳", username);
        }
    }

    @MessageMapping("/sync-state")
    public void handleSyncState(Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = getUsernameFromSession(headerAccessor);
        if (username != null) {
            onlineUserService.updateHeartbeat(username);

            Set<String> onlineUsers = onlineUserService.getOnlineUsers();
            messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
        }
    }

    private String getUsernameFromSession(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            return (String) sessionAttributes.get("authenticatedUser");
        }
        return null;
    }

    @MessageMapping("/private.message")
    public void sendPrivateMessage(Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String receiverName = payload.get("receiver");
        String content = payload.get("content");

        try {
            Message message = chatService.sendPrivateMessage(receiverName, content, headerAccessor);

            User currentUser = userService.getCurrentUser(headerAccessor);
            if (currentUser != null) {
                String senderUsername = currentUser.getUsername();

                messagingTemplate.convertAndSendToUser(senderUsername, "/queue/private-messages", message);

                String receiverUsername = onlineUserService.getUsernameByName(receiverName);
                if (receiverUsername != null && onlineUserService.isOnline(receiverUsername) && !senderUsername.equals(receiverUsername)) {
                    messagingTemplate.convertAndSendToUser(receiverUsername, "/queue/private-messages", message);
                }  else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("type", "message_sent");
                    result.put("message", message);
                    result.put("receiverOffline", receiverName != null && !receiverName.isEmpty());
                    result.put("sender", currentUser.getName());
                    result.put("receiver", receiverName);
                    messagingTemplate.convertAndSendToUser(senderUsername, "/queue/private-messages", result);
                }
            }
        } catch (Exception e) {
            log.error("发送私聊消息失败", e);
            sendErrorMessage(headerAccessor, "发送失败：" + e.getMessage());
        }
    }

    private void sendErrorMessage(SimpMessageHeaderAccessor headerAccessor, String errorMessage) {
        try {
            User currentUser = userService.getCurrentUser(headerAccessor);
            if (currentUser != null) {
                Map<String, Object> error = new HashMap<>();
                error.put("type", "send_error");
                error.put("message", errorMessage);
                messagingTemplate.convertAndSendToUser(currentUser.getUsername(), "/queue/send-error", error);
            }
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
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

    @GetMapping("/api/private-history/{targetName}")
    @ResponseBody
    public List<Message> getPrivateHistory(@PathVariable String targetName, HttpServletRequest request) {
        try {
            User currentUser = userService.getCurrentUserWithGuest(request);

            if (currentUser == null) {
                return new ArrayList<>();
            }

            String currentName = currentUser.getName();
            List<Message> messages = chatService.getPrivateMessageHistory(currentName, targetName);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            log.error("获取私聊历史失败", e);
            return new ArrayList<>();
        }
    }
    @GetMapping("/api/current-user-info")
    @ResponseBody
    public Map<String, Object> getCurrentUserInfo(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = userService.getCurrentUserWithGuest(request);

        if (currentUser != null) {
            result.put("username", currentUser.getUsername());
            result.put("name", currentUser.getName());
            result.put("role", currentUser.getRole());
            result.put("isGuest", "ROLE_GUEST".equals(currentUser.getRole()));
        } else {
            result.put("isGuest", true);
        }

        return result;
    }
}

