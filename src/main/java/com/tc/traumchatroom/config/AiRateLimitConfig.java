package com.tc.traumchatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 调用限流配置 — 从 application.yml 读取 ai.rate-limit.xxx 配置项
 * 用于限制 AI 助手（小汤）的调用频率
 */
@Configuration
@ConfigurationProperties(prefix = "ai.rate-limit")
@Data
public class AiRateLimitConfig {
    /** 每用户每分钟最大调用次数（缺省 3，与历史硬编码一致） */
    private int maxPerMinute = 3;
}
