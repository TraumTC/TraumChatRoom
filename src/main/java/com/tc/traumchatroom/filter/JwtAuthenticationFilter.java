package com.tc.traumchatroom.filter;

import com.tc.traumchatroom.util.JwtUtil;
import com.tc.traumchatroom.service.RefreshTokenStore;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * 工作流程：
 * 1. 从请求头提取 Token（Authorization: Bearer xxx）
 * 2. 验证 Token 是否有效
 * 3. 从 Token 中解析用户名，加载用户详情
 * 4. 设置到 SecurityContext 中（这样后续接口就能拿到当前用户）
 *
 * 继承 OncePerRequestFilter：保证每个请求只过滤一次
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserDetailsService userDetailsService;

    @Resource
    private RefreshTokenStore refreshTokenStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // 1. 从请求头提取 Token
        String token = extractToken(request);

        // 2. 如果 Token 有效，设置认证信息
        if (StringUtils.hasText(token) && jwtUtil.validateAccessToken(token)) {
            // 从 Token 中解析用户名
            String username = jwtUtil.getUsernameFromToken(token);
            String sessionId = jwtUtil.getSessionIdFromToken(token);

            boolean sessionActive;
            try {
                sessionActive = refreshTokenStore.isSessionActive(username, sessionId);
            } catch (com.tc.traumchatroom.exception.BusinessException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":503,\"message\":\"认证会话服务暂时不可用\",\"data\":null}");
                return;
            }
            if (!sessionActive) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            try {
                // 加载用户详情（包含密码和权限）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 创建认证令牌（第三个参数是权限列表）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,  // 密码不需要（已通过 Token 认证）
                                userDetails.getAuthorities()
                        );

                // 设置请求详情（IP、Session 等）
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 4. 存入 SecurityContext（后续通过 SecurityContextHolder.getContext().getAuthentication() 获取）
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                // 用户不存在（游客过期 / 被删除）：清空认证态，让 Security 返回 401 而非 500
                SecurityContextHolder.clearContext();
            }
        }

        // 5. 继续执行后续过滤器
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取 Bearer Token
     * 格式：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);  // 去掉 "Bearer " 前缀
        }
        return null;
    }
}
