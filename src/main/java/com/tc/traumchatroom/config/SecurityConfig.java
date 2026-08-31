package com.tc.traumchatroom.config;

import com.tc.traumchatroom.filter.JwtAuthenticationFilter;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置
 *
 * 核心职责：
 * 1. 配置哪些 URL 需要认证，哪些公开访问
 * 2. 配置无状态 Session（JWT 模式）
 * 3. 注册 JWT 过滤器
 * 4. 配置 CORS（与 WebMvcConfig 配合）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * CORS 白名单（与 WebMvcConfig 一致，禁止通配符 *）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 密码编码器（BCrypt）
     * BCrypt 是目前最安全的密码哈希算法，每次生成的哈希值都不同（带随机盐）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器（用于手动调用认证，如登录接口）
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链 — 核心配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. 禁用 CSRF（前后端分离用 JWT，不需要 CSRF 保护）
            .csrf(csrf -> csrf.disable())

            // 2. 配置 CORS（白名单，禁止 *）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3. 配置 Session 策略为无状态（不创建 HttpSession）
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 4. 配置 URL 权限规则
            .authorizeHttpRequests(auth -> auth
                // 公开访问的接口（不需要认证）
                .requestMatchers("/api/auth/register").permitAll()    // 注册
                .requestMatchers("/api/auth/login").permitAll()       // 登录
                .requestMatchers("/api/auth/refresh").permitAll()     // 刷新 Token
                .requestMatchers("/api/auth/logout").permitAll()      // 允许仅凭 refresh Cookie 登出并清理
                .requestMatchers("/api/auth/guest").permitAll()       // 游客登录
                .requestMatchers(HttpMethod.GET, "/api/file/download/**").permitAll()  // 文件下载
                .requestMatchers("/ws/**").permitAll()                 // WebSocket 端点

                // 管理员接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 其他所有接口需要认证（包括 /api/auth/me 和 /api/auth/logout）
                .anyRequest().authenticated()
            )

            // 5. 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // 6. 未认证时的处理（返回 401 JSON 而不是默认的 HTML 登录页）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token过期\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无权限\",\"data\":null}");
                })
            );

        return http.build();
    }
}
