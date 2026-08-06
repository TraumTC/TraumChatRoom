package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RefreshRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.dto.response.LoginResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.response.UserResponse;
import com.tc.traumchatroom.service.AuthService;
import com.tc.traumchatroom.annotation.LogOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 路径前缀：/api/auth
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 注册新用户
     * POST /api/auth/register
     */
    @LogOperation(action = "REGISTER", targetType = "user")
    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return Result.success(user);
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @LogOperation(action = "LOGIN", targetType = "user")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return Result.success(response);
    }

    /**
     * 刷新 Token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request);
        return Result.success(response);
    }

    /**
     * 登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return Result.success();
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/me
     * 通过 SecurityContextHolder 获取已认证的用户名（JWT Filter 已设置好）
     */
    @GetMapping("/me")
    public Result<UserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();  // 从 SecurityContext 获取用户名
        return Result.success(authService.getCurrentUser(username));
    }

    /**
     * 游客登录
     * POST /api/auth/guest
     */
    @PostMapping("/guest")
    public Result<LoginResponse> guest(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIp(request);
        LoginResponse response = authService.loginAsGuest(userAgent, clientIp);
        return Result.success(response);
    }

    /**
     * 获取客户端真实 IP（支持反向代理 X-Forwarded-For）
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
