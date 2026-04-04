package com.tc.traumchatroom.util;


import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
@Component
public class OnlineUserUtil {

    private final Set<String> onlineUsers = Collections.synchronizedSet(new HashSet<>());
    public void addUser(String username) {
        onlineUsers.add(username);
    }
    /**
     * 移除在线用户
     */
    public void removeUser(String username) {
        onlineUsers.remove(username);
    }
    /**
     * 获取在线用户列表
     */
    public Set<String> getOnlineUsers() {
        return new HashSet<>(onlineUsers);
    }
    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        return onlineUsers.size();
    }
    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String username) {
        return onlineUsers.contains(username);
    }
}
