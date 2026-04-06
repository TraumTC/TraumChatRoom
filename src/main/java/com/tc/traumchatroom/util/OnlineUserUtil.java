package com.tc.traumchatroom.util;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserUtil {

    private final Map<String, Long> onlineUsersWithTime = new ConcurrentHashMap<>();

    private final Map<String, String> usernameToName = new ConcurrentHashMap<>();

    public void addUser(String username, String name) {
        onlineUsersWithTime.put(username, System.currentTimeMillis());
        usernameToName.put(username, name != null ? name : username);
    }

    public void removeUser(String username) {
        onlineUsersWithTime.remove(username);
        usernameToName.remove(username);
    }

    public Set<String> getOnlineUsers() {
        return new HashSet<>(usernameToName.values());
    }

    public Set<String> getOnlineUsernames() {
        return new HashSet<>(onlineUsersWithTime.keySet());
    }

    public Map<String, Long> getOnlineUsersWithTime() {
        return Collections.unmodifiableMap(onlineUsersWithTime);
    }

    public Map<String, String> getUsernameToNameMap() {
        return Collections.unmodifiableMap(usernameToName);
    }

    public int getOnlineCount() {
        return onlineUsersWithTime.size();
    }

    public boolean isOnline(String username) {
        return onlineUsersWithTime.containsKey(username);
    }

    public String getUsernameByName(String name) {
        for (Map.Entry<String, String> entry : usernameToName.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getNameByUsername(String username) {
        return usernameToName.get(username);
    }

    public void updateHeartbeat(String username) {
        if (onlineUsersWithTime.containsKey(username)) {
            onlineUsersWithTime.put(username, System.currentTimeMillis());
        }
    }

    public long getLastHeartbeat(String username) {
        Long timestamp = onlineUsersWithTime.get(username);
        return timestamp != null ? timestamp : 0;
    }
}

