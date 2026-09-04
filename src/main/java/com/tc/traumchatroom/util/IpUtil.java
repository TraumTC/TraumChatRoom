package com.tc.traumchatroom.util;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 客户端 IP 解析工具（全项目唯一入口，P0-4）
 *
 * 三种模式，由 {@link #configureTrustedProxies} 决定：
 *
 * <h3>默认（未配置可信代理）＝ 历史行为</h3>
 * 无条件信任转发头：X-Forwarded-For 首项 → X-Real-IP → 直接连接地址。
 * 这是项目长期以来的行为，本地直连（无代理）与早期部署都不受影响。
 *
 * <h3>严格模式（配置了可信代理）＝ 修复 XFF 伪造</h3>
 * 只有「直接连接方」（request.getRemoteAddr()，Socket 层不可伪造）属于可信代理时，
 * 才采信转发头；否则忽略全部转发头，直接返回直连地址。
 *
 * 在可信代理场景下解析 XFF 是从右往左跳过可信代理地址、取第一个非可信项：
 * 攻击者即便在请求里伪造 X-Forwarded-For，nginx 用 $proxy_add_x_forwarded_for 追加真实
 * IP 后形如 "伪造IP, 真实IP"，右侧的非可信项才是真实客户端 —— 伪造的前缀被跳过。
 *
 * 注意：可信代理必须正确配置，否则来自非代理直连的请求（如公网直接暴露、内网直连）
 * 会因忽略转发头而拿不到真实 IP。生产部署文档要求设置该项。
 */
public final class IpUtil {

    /** 可信代理匹配器（IP 或 CIDR）。空 = 历史行为：无条件信任转发头。 */
    private static volatile List<IpAddressMatcher> trustedProxies = List.of();

    private IpUtil() {
    }

    /**
     * 注入可信代理列表（IP 或 CIDR，如 "127.0.0.1", "10.0.0.0/8", "::1"）。
     * 空列表 = 恢复历史行为。由 Spring 配置类在启动时调用。
     */
    public static void configureTrustedProxies(List<String> proxies) {
        if (proxies == null || proxies.isEmpty()) {
            trustedProxies = List.of();
            return;
        }
        trustedProxies = proxies.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(IpAddressMatcher::new)
                .toList();
    }

    /**
     * 从 HTTP 请求解析客户端 IP
     */
    public static String fromHttp(HttpServletRequest request) {
        if (request == null) return null;
        return resolve(
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP")
        );
    }

    /**
     * 从 WebSocket 握手请求解析客户端 IP
     */
    public static String fromWebSocket(ServerHttpRequest request) {
        if (request == null) return null;
        String remoteAddr = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;
        if (remoteAddr == null) return null;
        return resolve(
                remoteAddr,
                request.getHeaders().getFirst("X-Forwarded-For"),
                request.getHeaders().getFirst("X-Real-IP")
        );
    }

    private static String resolve(String remoteAddr, String xff, String xReal) {
        if (trustedProxies.isEmpty()) {
            // 历史行为：无条件信任转发头
            return resolveFromForwardedHeaders(xff, xReal, remoteAddr);
        }
        if (!isTrustedProxy(remoteAddr)) {
            // 直连方不可信：忽略可能伪造的转发头，返回 Socket 层真实地址
            return remoteAddr;
        }
        // 直连方是可信代理：采信转发头
        return resolveFromForwardedHeaders(xff, xReal, remoteAddr);
    }

    private static String resolveFromForwardedHeaders(String xff, String xReal, String remoteAddr) {
        if (trustedProxies.isEmpty()) {
            // 历史模式：取 XFF 首项（与旧实现完全一致）
            String first = firstXffEntry(xff);
            if (first != null) return first;
        } else {
            // 严格模式：从右往左跳过可信代理，取第一个非可信项（防伪造前缀）
            String client = rightmostNonTrustedEntry(xff);
            if (client != null) return client;
        }
        if (StringUtils.hasText(xReal)) return xReal.trim();
        return remoteAddr;
    }

    /** 取 X-Forwarded-For 首项（历史模式）；空/无内容返回 null */
    private static String firstXffEntry(String xff) {
        if (!StringUtils.hasText(xff)) return null;
        String first = xff.split(",")[0].trim();
        return StringUtils.hasText(first) ? first : null;
    }

    /** 严格模式：从右往左跳过可信代理地址，返回第一个非可信项；全可信/无内容返回 null */
    private static String rightmostNonTrustedEntry(String xff) {
        if (!StringUtils.hasText(xff)) return null;
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = parts[i].trim();
            if (StringUtils.hasText(ip) && !isTrustedProxy(ip)) {
                return ip;
            }
        }
        return null;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(ip)) return true;
        }
        return false;
    }
}
