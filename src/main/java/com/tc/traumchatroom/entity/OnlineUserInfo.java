package com.tc.traumchatroom.entity;

import lombok.Data;
import java.util.HashSet;
import java.util.Set;

/**
 * 在线用户聚合对象（非数据库表）
 * 用于WebSocket推送在线用户信息
 */
@Data
public class OnlineUserInfo {
    /** 在线用户数量 */
    private int count;
    /** 在线用户昵称集合 */
    private Set<String> onlineUsers = new HashSet<>();
}
