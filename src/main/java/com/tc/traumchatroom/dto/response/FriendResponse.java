package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友信息响应 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendResponse {
    private Integer id;
    private String username;
    private String name;
    private String avatar;
    private String remark;      // 我给对方的备注
    private boolean online;     // 是否在线
    private LocalDateTime lastActiveTime;
}
