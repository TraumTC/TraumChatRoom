package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可@用户响应 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MentionableUserResponse {
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String name;
    /** 头像URL */
    private String avatar;
    /** 是否为AI */
    private boolean isAi;
}
