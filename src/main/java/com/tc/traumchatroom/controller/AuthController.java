package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.dto.response.LoginResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.response.UserResponse;
import com.tc.traumchatroom.service.AuthService;
import com.tc.traumchatroom.util.JwtUtil;
import com.tc.traumchatroom.annotation.LogOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
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

    @Resource
    private JwtUtil jwtUtil;

    @Value("${jwt.refresh-cookie-name:refreshToken}")
    private String refreshCookieName;

    @Value("${jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

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
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        writeRefreshCookie(httpResponse, response);
        return Result.success(response);
    }

    /**
     * 刷新 Token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String token = readRefreshCookie(httpRequest);
        LoginResponse response = authService.refresh(token);
        writeRefreshCookie(httpResponse, response);
        return Result.success(response);
    }

    /**
     * 登出
     * POST /api/auth/logout
     */
    @LogOperation(action = "LOGOUT", targetType = "user")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        String token = readRefreshCookie(httpRequest);
        try {
            if (token != null) authService.logout(token);
            return Result.success();
        } finally {
            // 服务端撤销失败时也清除浏览器 Cookie，避免用户刷新后再次自动登录。
            clearRefreshCookie(httpResponse);
        }
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
    @LogOperation(action = "GUEST_LOGIN", targetType = "user")
    @PostMapping("/guest")
    public Result<LoginResponse> guest(HttpServletRequest request, HttpServletResponse httpResponse) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIp(request);
        LoginResponse response = authService.loginAsGuest(userAgent, clientIp);
        writeRefreshCookie(httpResponse, response);
        return Result.success(response);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (refreshCookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, LoginResponse loginResponse) {
        String token = loginResponse.getRefreshToken();
        if (token == null) return;
        long maxAge = Math.max(1, jwtUtil.getRemainingValidityMillis(token) / 1000);
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
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
