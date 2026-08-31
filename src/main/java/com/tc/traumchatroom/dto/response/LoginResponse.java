package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 登录响应 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    /** 访问令牌 */
    private String accessToken;
    /**
     * 刷新令牌仅供后端控制器写入 HttpOnly Cookie，禁止序列化到响应体。
     * WRITE_ONLY 保留了后端内部取值能力，也兼容旧 DTO 构造方式。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String refreshToken;
    /** 过期时间（秒） */
    private long expiresIn;
    /** 用户信息 */
    private UserResponse user;
}
