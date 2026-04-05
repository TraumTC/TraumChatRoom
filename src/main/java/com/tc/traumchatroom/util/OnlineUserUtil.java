package com.tc.traumchatroom.util;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserUtil {

    private final Map<String, Long> onlineUsers = new ConcurrentHashMap<>();

    public void addUser(String username) {
        onlineUsers.put(username, System.currentTimeMillis());
    }

    public void removeUser(String username) {
        onlineUsers.remove(username);
    }

    public Set<String> getOnlineUsers() {
        return new HashSet<>(onlineUsers.keySet());
    }

    public Map<String, Long> getOnlineUsersWithTime() {
        return Collections.unmodifiableMap(onlineUsers);
    }

    public int getOnlineCount() {
        return onlineUsers.size();
    }

    public boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
}
