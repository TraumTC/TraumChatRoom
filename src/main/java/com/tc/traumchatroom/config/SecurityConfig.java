package com.tc.traumchatroom.config;

import com.tc.traumchatroom.filter.JwtAuthenticationFilter;
import com.tc.traumchatroom.service.UserDetailsService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 安全配置类
 * 负责配置应用程序的认证、授权、CORS、CSRF、登录登出等安全策略
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 注入自定义的用户详情服务，用于从数据库加载用户信息
     */
    @Resource
    private UserDetailsService userDetailsService;

    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置密码加密器，使用 BCrypt 强哈希算法对用户密码进行加密和验证
     * BCrypt 会自动生成随机盐值，安全性高
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 DaoAuthenticationProvider，用于基于数据库的用户认证
     * 将自定义的 UserDetailsService 和密码加密器注入到认证提供者中
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 配置认证管理器，用于处理登录时的认证逻辑
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置跨域资源共享（CORS）策略
     * 允许所有来源访问，支持常见的 HTTP 方法，允许携带凭证（如 Cookie）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            // 允许所有来源（生产环境建议限制为具体域名）
            configuration.setAllowedOriginPatterns(List.of("*"));
            // 允许携带 Cookie 等凭证信息
            configuration.setAllowCredentials(true);
            // 允许的 HTTP 请求方法
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            // 允许所有请求头
            configuration.setAllowedHeaders(List.of("*"));
            return configuration;
        };
    }

    /**
     * 配置安全过滤链，定义各类请求的安全策略
     * 包括 CSRF 防护、CORS、权限控制、登录登出、异常处理等
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 配置 CSRF 防护，对 WebSocket、API 接口及登录注册相关路径禁用 CSRF
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/ws/**", "/api/**", "/login","/logout","/register")
                )
                // 关闭 Session，使用 JWT 无状态认证
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 启用并配置 CORS 跨域支持
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 配置 HTTP 响应头
                .headers(headers -> headers
                        // 禁用 X-Frame-Options，允许页面在 iframe 中嵌入（开发环境需要）
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        // 禁用 Content-Type-Options，允许第三方内容类型解析
                        .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                )
                // 配置请求授权规则
                .authorizeHttpRequests( auth -> auth
                        // 以下路径允许匿名访问，无需登录
                        .requestMatchers("/", "/register", "/login", "/api/login", "/api/logout", "/error", "/space",
                                "/ws/**", "/api/current-user", "/api/current-user-info",
                                "/history", "/api/online-users", "/api/private-history/**",
                                "/api/mentionable-users", "/api/file/**", "/css/**", "/js/**", "/photo/**", "/favicon.ico",
                                "/admin-users.html", "/profile.html", "/one.html").permitAll()
                        // 管理员相关接口需要 ADMIN 角色权限
                        .requestMatchers("/admin/users", "/api/admin/**").hasRole("ADMIN")
                        // 其余所有请求需要登录后才能访问
                        .anyRequest().authenticated()
                )
                // 禁用表单登录，使用 JWT
                .formLogin(AbstractHttpConfigurer::disable)
                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 配置异常处理，主要用于未认证用户的访问拦截
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String requestUri = request.getRequestURI();
                            // API 请求返回 401 JSON
                            if (requestUri.startsWith("/api/")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"success\":false,\"message\":\"未登录或登录已过期\"}");
                            } else if (requestUri.equals("/login")) {
                                // 如果访问的是登录页，重定向到主页（已登录用户再次访问登录页的场景）
                                response.sendRedirect("/space");
                            } else {
                                // 未认证用户访问受保护资源，重定向到登录页
                                response.sendRedirect("/login");
                            }
                        })
                );
        return http.build();
    }

    /**
     * 配置 ForwardedHeaderFilter，用于处理反向代理（如 Nginx）转发的请求头
     * 确保应用能正确获取客户端的真实 IP、协议和主机名等信息
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
