package com.tc.traumchatroom.service;

import java.util.Set;

/**
 * 在线用户服务接口
 * 使用 Redis Set 存储在线用户
 */
public interface OnlineUserService {

    /**
     * 用户上线
     * @param username 用户名
     */
    void userOnline(String username);

    /**
     * 用户下线
     * @param username 用户名
     */
    void userOffline(String username);

    /**
     * 获取所有在线用户
     * @return 在线用户集合
     */
    Set<String> getOnlineUsers();

    /**
     * 获取在线用户数量
     */
    int getOnlineCount();

    /**
     * 用户是否在线
     */
    boolean isOnline(String username);

    /**
     * 更新用户心跳
     */
    void updateHeartbeat(String username);
}
