package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体 — 对应 user 表
 */
@Data
public class User {
    /** 用户ID，自增主键 */
    private Integer id;
    /** 登录用户名，2-20字符，唯一 */
    private String username;
    /** 显示昵称，1-20字符，唯一 */
    private String name;
    /** 密码，BCrypt加密后约60字符 */
    private String password;
    /** 角色：ROLE_USER / ROLE_ADMIN / ROLE_GUEST */
    private String role;
    /** 自定义头像URL，NULL时使用默认首字头像 */
    private String avatar;
    /** 账号状态：1正常 0禁用 */
    private Integer status;
    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;
    /** 注册时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 软删除时间，NULL表示未删除 */
    private LocalDateTime deletedAt;
}
