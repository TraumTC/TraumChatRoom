package com.tc.traumchatroom.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnlineUserInfo {
    private int count;
    private Set<String> onlineUsers;
}
