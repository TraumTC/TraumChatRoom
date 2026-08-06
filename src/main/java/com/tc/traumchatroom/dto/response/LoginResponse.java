package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    /** 访问令牌 */
    private String accessToken;
    /** 刷新令牌 */
    private String refreshToken;
    /** 过期时间（秒） */
    private long expiresIn;
    /** 用户信息 */
    private UserResponse user;
}
