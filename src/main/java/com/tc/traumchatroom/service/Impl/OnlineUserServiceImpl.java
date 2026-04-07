package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.service.OnlineUserService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlineUserServiceImpl implements OnlineUserService {

    private final Map<String, Long> onlineUsersWithTime = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToName = new ConcurrentHashMap<>();

    @Override
    public void addUser(String username, String name) {
        onlineUsersWithTime.put(username, System.currentTimeMillis());
        usernameToName.put(username, name != null ? name : username);
    }

    @Override
    public void removeUser(String username) {
        onlineUsersWithTime.remove(username);
        usernameToName.remove(username);
    }

    @Override
    public Set<String> getOnlineUsers() {
        return new HashSet<>(usernameToName.values());
    }

    @Override
    public Set<String> getOnlineUsernames() {
        return new HashSet<>(onlineUsersWithTime.keySet());
    }

    @Override
    public Map<String, Long> getOnlineUsersWithTime() {
        return Collections.unmodifiableMap(onlineUsersWithTime);
    }

    @Override
    public Map<String, String> getUsernameToNameMap() {
        return Collections.unmodifiableMap(usernameToName);
    }

    @Override
    public int getOnlineCount() {
        return onlineUsersWithTime.size();
    }

    @Override
    public boolean isOnline(String username) {
        return onlineUsersWithTime.containsKey(username);
    }

    @Override
    public String getUsernameByName(String name) {
        for (Map.Entry<String, String> entry : usernameToName.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public String getNameByUsername(String username) {
        return usernameToName.get(username);
    }

    @Override
    public void updateHeartbeat(String username) {
        if (onlineUsersWithTime.containsKey(username)) {
            onlineUsersWithTime.put(username, System.currentTimeMillis());
        }
    }

    @Override
    public long getLastHeartbeat(String username) {
        Long timestamp = onlineUsersWithTime.get(username);
        return timestamp != null ? timestamp : 0;
    }
}
