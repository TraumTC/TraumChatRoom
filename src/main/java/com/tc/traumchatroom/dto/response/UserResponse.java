package com.tc.traumchatroom.dto.response;

import com.tc.traumchatroom.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO
 * 注意：不包含 password 字段，防止密码泄露给前端
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    /** 用户ID */
    private Integer id;
    /** 登录用户名 */
    private String username;
    /** 显示昵称 */
    private String name;
    /** 角色：ROLE_USER / ROLE_ADMIN / ROLE_GUEST */
    private String role;
    /** 头像URL */
    private String avatar;
    /** 账号状态：1正常 0禁用 */
    private Integer status;
    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;
    /** 注册时间 */
    private LocalDateTime createdAt;

    /**
     * 从实体类转换（去掉敏感字段 password）
     */
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                user.getAvatar(),
                user.getStatus(),
                user.getLastActiveTime(),
                user.getCreatedAt()
        );
    }
}
