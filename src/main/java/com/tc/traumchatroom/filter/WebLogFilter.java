package com.tc.traumchatroom.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 日志过滤器
 */
@Component
@Slf4j
public class WebLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = getClientIp(request);

        // 请求进入
        log.info("-> {} {} {} {}", method, uri, ip, request.getHeader("User-Agent"));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // 请求离开
            if (duration > 1000) { // 对于超过1秒的请求，记录警告
                log.warn("慢请求: <- {} {} {}ms {}", method, uri, duration, response.getStatus());
            } else {
                log.info("<- {} {} {}ms {}", method, uri, duration, response.getStatus());
            }

        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // 忽略常见静态资源路径
        return uri.startsWith("/static/") ||
                uri.startsWith("/assets/") ||
                uri.startsWith("/uploads/") ||
                uri.startsWith("/webjars/") ||
                uri.startsWith("/favicon.ico") ||
                uri.matches(".*\\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
