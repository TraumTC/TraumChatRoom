package com.tc.traumchatroom.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class WebSocketSessionService {

    private final OnlineUserService onlineUserService;

    public WebSocketSessionService(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    public void handleUserConnected(String username, String name) {
        String displayName = name != null ? name : username;
        onlineUserService.addUser(username, displayName);
    }

    public void handleUserDisconnected(String username) {
        onlineUserService.removeUser(username);
    }

    public void handleHeartbeat(String username) {
        onlineUserService.updateHeartbeat(username);
    }

    public String getNameByUsername(String username) {
        return onlineUserService.getNameByUsername(username);
    }

    public Map<String, Long> getOnlineUsersWithTime() {
        return onlineUserService.getOnlineUsersWithTime();
    }
}
