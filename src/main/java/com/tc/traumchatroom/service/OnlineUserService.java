package com.tc.traumchatroom.service;

import java.util.Map;
import java.util.Set;

public interface OnlineUserService {
    void addUser(String username, String name);
    void removeUser(String username);
    Set<String> getOnlineUsers();
    Set<String> getOnlineUsernames();
    Map<String, Long> getOnlineUsersWithTime();
    Map<String, String> getUsernameToNameMap();
    int getOnlineCount();
    boolean isOnline(String username);
    String getUsernameByName(String name);
    String getNameByUsername(String username);
    void updateHeartbeat(String username);
    long getLastHeartbeat(String username);
}
