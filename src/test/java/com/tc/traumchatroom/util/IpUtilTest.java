package com.tc.traumchatroom.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IpUtil 单元测试（P0-4 回归）
 *
 * 三种模式：
 * 1. 未配置可信代理（默认）＝ 历史行为：无条件信任 XFF 首项，行为与旧实现逐字节一致。
 * 2. 配置可信代理（严格）＝ 仅当 remoteAddr 属于可信代理时才采信转发头。
 * 3. 非可信直连（严格模式）＝ 忽略伪造的转发头，直接返回 Socket 层地址 —— 修复 XFF 伪造绕过。
 *
 * 每个用例结束必须复位可信代理配置（静态字段），否则用例间互相污染。
 */
@ExtendWith(MockitoExtension.class)
class IpUtilTest {

    @AfterEach
    void reset() {
        IpUtil.configureTrustedProxies(List.of());
    }

    // ---------- 模式 1：未配置（历史行为，必须与旧实现一致） ----------

    @Test
    void defaultTrustsXffFirstEntry() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("203.0.113.9");
    }

    @Test
    void defaultFallsBackToRemoteAddrWhenNoForwardedHeaders() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void defaultReadsXRealIpWhenNoXff() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("198.51.100.7");
    }

    // ---------- 模式 2：严格模式，可信代理发来的请求 ----------

    @Test
    void strictUsesRightmostNonTrustedXffEntry() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        // nginx 用 $proxy_add_x_forwarded_for 追加真实 IP：形如 "攻击者伪造的前缀, 真实IP"
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");  // 可信代理
        req.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");  // 伪造前缀 + 真实客户端

        // 取右数第一个非可信项 = 真实客户端，伪造前缀被跳过
        assertThat(IpUtil.fromHttp(req)).isEqualTo("203.0.113.9");
    }

    @Test
    void strictTrustsProxyChainSkippingTrustedHops() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1", "10.0.0.0/8"));

        // 真实链路：客户端 203.0.113.9 → 内网代理 10.0.0.5 → 本机 nginx 127.0.0.1
        // nginx 用 $proxy_add_x_forwarded_for 追加最近一跳：XFF = "客户端, 内网代理"
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.5");

        // 从右往左：10.0.0.5 是可信代理 → 跳过；203.0.113.9 非可信 → 取之（原始客户端）
        assertThat(IpUtil.fromHttp(req)).isEqualTo("203.0.113.9");
    }

    @Test
    void strictFallsBackToXRealIpWhenXffEmpty() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Real-IP", "203.0.113.9");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("203.0.113.9");
    }

    @Test
    void strictFallsBackToRemoteAddrWhenNoForwardedHeaders() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("127.0.0.1");
    }

    // ---------- 模式 3：严格模式，非可信直连（核心安全修复） ----------

    @Test
    void strictIgnoresSpoofedForwardedHeadersFromUntrustedDirectPeer() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        // 攻击者绕过代理直接连到应用（remoteAddr 非可信），伪造任意 XFF / X-Real-IP
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8");
        req.addHeader("X-Forwarded-For", "1.1.1.1");
        req.addHeader("X-Real-IP", "2.2.2.2");

        // 必须忽略转发头，返回 Socket 层真实地址
        assertThat(IpUtil.fromHttp(req)).isEqualTo("8.8.8.8");
    }

    @Test
    void strictCidrTrustedProxyIsRecognized() {
        IpUtil.configureTrustedProxies(List.of("10.0.0.0/8"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.1.2.3");
        req.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(IpUtil.fromHttp(req)).isEqualTo("203.0.113.9");
    }

    // ---------- WebSocket 握手路径 ----------

    @Test
    void fromWebSocketStrictIgnoresSpoofedXff() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "1.1.1.1");
        ServerHttpRequest ws = mock(ServerHttpRequest.class);
        when(ws.getHeaders()).thenReturn(headers);
        // 非可信直连（SockJS 客户端直连后端）
        when(ws.getRemoteAddress()).thenReturn(new InetSocketAddress("8.8.8.8", 50000));

        assertThat(IpUtil.fromWebSocket(ws)).isEqualTo("8.8.8.8");
    }

    @Test
    void fromWebSocketStrictReadsRealClientFromTrustedProxy() {
        IpUtil.configureTrustedProxies(List.of("127.0.0.1"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.9");
        ServerHttpRequest ws = mock(ServerHttpRequest.class);
        when(ws.getHeaders()).thenReturn(headers);
        when(ws.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 50000));

        assertThat(IpUtil.fromWebSocket(ws)).isEqualTo("203.0.113.9");
    }

    @Test
    void fromWebSocketDefaultBehavesLikeBefore() {
        // 未配置可信代理时，WS 路径与旧实现一致：取 XFF 首项
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.9, 10.0.0.2");
        ServerHttpRequest ws = mock(ServerHttpRequest.class);
        when(ws.getHeaders()).thenReturn(headers);
        when(ws.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 50000));

        assertThat(IpUtil.fromWebSocket(ws)).isEqualTo("203.0.113.9");
    }
}
