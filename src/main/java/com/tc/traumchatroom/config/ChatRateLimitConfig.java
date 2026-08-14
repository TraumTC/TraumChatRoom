package com.tc.traumchatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天限流配置 — 从 application.yml 读取 chat.rate-limit.xxx 配置项
 * 用于限制消息发送频率
 */
@Configuration
@ConfigurationProperties(prefix = "chat.rate-limit")
@Data
public class ChatRateLimitConfig {
    /** 消息发送限流：每用户每分钟最大发送条数（缺省 30，与历史硬编码一致） */
    private int sendMaxPerMinute = 30;
}
