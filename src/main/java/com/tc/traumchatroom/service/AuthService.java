package com.tc.traumchatroom.service;

import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RefreshRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.dto.response.LoginResponse;
import com.tc.traumchatroom.dto.response.UserResponse;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 注册新用户
     * @param request 注册信息
     * @return 注册成功的用户信息
     */
    UserResponse register(RegisterRequest request);

    /**
     * 用户登录（含登录频率限制）
     * @param request 登录信息
     * @param clientIp 客户端IP（用于IP维度限流）
     * @return Token + 用户信息
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 刷新访问令牌
     * @param request refreshToken
     * @return 新的访问令牌
     */
    LoginResponse refresh(RefreshRequest request);

    /**
     * 登出（删除 refreshToken）
     * @param refreshToken 刷新令牌
     */
    void logout(String refreshToken);

    /**
     * 获取当前用户信息
     * @param username 用户名
     * @return 用户信息
     */
    UserResponse getCurrentUser(String username);

    /**
     * 游客登录（自动生成游客身份）
     * @param userAgent 浏览器UA
     * @param clientIp 客户端IP
     * @return Token + 游客信息
     */
    LoginResponse loginAsGuest(String userAgent, String clientIp);
}
