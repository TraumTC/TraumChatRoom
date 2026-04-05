package com.tc.traumchatroom.util;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserUtil {

    // username -> 连接时间戳（用于心跳检测）
    private final Map<String, Long> onlineUsersWithTime = new ConcurrentHashMap<>();

    // username -> name（昵称）的映射
    private final Map<String, String> usernameToName = new ConcurrentHashMap<>();

    public void addUser(String username, String name) {
        onlineUsersWithTime.put(username, System.currentTimeMillis());
        usernameToName.put(username, name != null ? name : username);
    }

    public void removeUser(String username) {
        onlineUsersWithTime.remove(username);
        usernameToName.remove(username);
    }

    // 返回 name（昵称）集合（用于前端显示）
    public Set<String> getOnlineUsers() {
        return new HashSet<>(usernameToName.values());
    }

    // 返回 username 集合（用于 WebSocket 推送）
    public Set<String> getOnlineUsernames() {
        return new HashSet<>(onlineUsersWithTime.keySet());
    }

    // 返回 username -> 时间戳的映射
    public Map<String, Long> getOnlineUsersWithTime() {
        return Collections.unmodifiableMap(onlineUsersWithTime);
    }

    // 返回 username -> name 的映射
    public Map<String, String> getUsernameToNameMap() {
        return Collections.unmodifiableMap(usernameToName);
    }

    public int getOnlineCount() {
        return onlineUsersWithTime.size();
    }

    public boolean isOnline(String username) {
        return onlineUsersWithTime.containsKey(username);
    }

    // 根据 name 查找 username
    public String getUsernameByName(String name) {
        for (Map.Entry<String, String> entry : usernameToName.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // 根据 username 查找 name
    public String getNameByUsername(String username) {
        return usernameToName.get(username);
    }

    // 更新时间戳（用于心跳）
    public void updateHeartbeat(String username) {
        if (onlineUsersWithTime.containsKey(username)) {
            onlineUsersWithTime.put(username, System.currentTimeMillis());
        }
    }
}
