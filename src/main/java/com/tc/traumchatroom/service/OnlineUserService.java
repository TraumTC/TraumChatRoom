package com.tc.traumchatroom.service;

import java.util.Set;

/**
 * 在线用户服务接口
 *
 * 在线状态按「会话」引用计数：同一账号可以在多台设备并行登录
 * （认证层用 JWT 的 sid claim 支持，见 JwtUtil），
 * 因此必须等最后一个会话断开才判定用户离线。
 * 读方法（isOnline / getOnlineUsers / getOnlineCount）仍以用户为粒度。
 */
public interface OnlineUserService {

    /**
     * 注册一个用户会话（WebSocket 连接建立时调用）
     *
     * @param username  用户名
     * @param sessionId STOMP 会话 ID，用于区分同一用户的多个设备/标签页
     * @return true 表示这是该用户当前唯一存活的会话，即发生了「离线 → 在线」跃迁；
     *         false 表示该用户此前已有其它会话在线
     */
    boolean userOnline(String username, String sessionId);

    /**
     * 注销一个用户会话（WebSocket 断开时调用）
     *
     * @param username  用户名
     * @param sessionId STOMP 会话 ID
     * @return true 表示这是该用户最后一个会话，即发生了「在线 → 离线」跃迁；
     *         false 表示该用户还有其它设备在线，用户整体仍视为在线
     */
    boolean userOffline(String username, String sessionId);

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
     * 用户是否在线（任意一个设备在线即为在线）
     */
    boolean isOnline(String username);

    /**
     * 更新某个会话的心跳
     *
     * @param username  用户名
     * @param sessionId STOMP 会话 ID
     */
    void updateHeartbeat(String username, String sessionId);
}
