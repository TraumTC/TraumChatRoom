package com.tc.traumchatroom.config;

import com.tc.traumchatroom.util.IpUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 可信代理配置（P0-4 修复的开关）。
 *
 * 空配置（默认）＝ 保持历史行为：无条件信任 X-Forwarded-For / X-Real-IP。
 * 配置了可信代理（逗号分隔的 IP 或 CIDR）＝ 严格模式：只有请求的直接来源
 * （request.getRemoteAddr()，Socket 层不可伪造）在可信列表内，才采信转发头，
 * 否则忽略 —— 攻击者无法再靠伪造 X-Forwarded-For 绕过 IP 维度登录锁定。
 *
 * 生产部署必须设置，否则来自公网直连的请求会因忽略转发头而拿不到真实客户端 IP。
 *
 * 配置示例（application.yml / 环境变量 APP_TRUSTED_PROXIES）：
 *   app.trusted-proxies: 127.0.0.1,10.0.0.0/8,::1
 */
@Slf4j
@Configuration
public class IpTrustedProxyConfig {

    @Value("${app.trusted-proxies:}")
    private String trustedProxies;

    @PostConstruct
    public void init() {
        List<String> proxies = parse(trustedProxies);
        IpUtil.configureTrustedProxies(proxies);
        if (proxies.isEmpty()) {
            log.info("IP 解析：未配置可信代理，保持历史行为（信任 X-Forwarded-For）");
        } else {
            log.info("IP 解析：已启用可信代理严格模式，可信代理列表 = {}", proxies);
        }
    }

    private List<String> parse(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
