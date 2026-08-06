package com.tc.traumchatroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — CORS 跨域
 * 前后端分离时，前端(5173端口)访问后端(8080端口)会被浏览器拦截
 * 配置 CORS 后，告诉浏览器允许跨域访问
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")                         // 对所有 /api/ 开头的接口生效
                .allowedOrigins(allowedOrigins.split(","))     // 允许的前端地址
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)                        // 允许携带 Cookie
                .maxAge(3600);                                 // 预检请求缓存 1 小时
    }
}
