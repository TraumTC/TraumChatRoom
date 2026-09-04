package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.dto.response.LoginResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.response.UserResponse;
import com.tc.traumchatroom.service.AuthService;
import com.tc.traumchatroom.util.IpUtil;
import com.tc.traumchatroom.util.JwtUtil;
import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.annotation.RateLimit;
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
     *
     * 按来源 IP 限流：注册是匿名接口，用户名/昵称都是 UNIQUE 且软删后永久保留
     * （见 AuthServiceImpl#register 的 findByXxxIncludingDeleted 查重），
     * 不限流可被脚本批量占名。
     *
     * 额度取 10/分钟而非更紧：@Valid 校验失败不消耗配额（参数解析与校验发生在
     * 本切面之前，MethodArgumentNotValidException 抛出时切面还没执行），
     * 但 USER_EXISTS / NAME_EXISTS 是方法体内抛的会计入 —— 用户反复试昵称撞名
     * 需要留余量。脚本刷号是几千次量级，10/分钟足够卡住。
     */
    @RateLimit(key = "register", maxRequests = 10, windowMillis = 60_000, by = RateLimit.By.IP)
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
        String clientIp = IpUtil.fromHttp(httpRequest);
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
     *
     * 按来源 IP 限流，窗口刻意取 2 小时 —— 与游客会话存活期相同
     * （chat:guest:* 的 TTL 与 AuthServiceImpl#GUEST_REFRESH_TTL 都是 2h），
     * 因此这个固定窗口计数实际等价于「每 IP 最多 5 个同时存活的游客」，
     * 不必额外维护一份「当前每 IP 有几个活跃游客」的集合。
     * 固定窗口在边界处有突刺（跨窗口瞬间可能拿到 10 个），对并发上限这种用途够了。
     *
     * 为什么必须限：游客不入库、username 每次都是新的，而发送限流按 username 计数
     * （WebSocketChatController#allowSend），刷号等于重置配额。该处已改为游客按 IP 计数
     * 封住刷屏，这里则封住 token 与 Redis key 的无限增长。
     */
    @RateLimit(key = "guest", maxRequests = 5, windowMillis = 7_200_000, by = RateLimit.By.IP)
    @LogOperation(action = "GUEST_LOGIN", targetType = "user")
    @PostMapping("/guest")
    public Result<LoginResponse> guest(HttpServletRequest request, HttpServletResponse httpResponse) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = IpUtil.fromHttp(request);
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
}
