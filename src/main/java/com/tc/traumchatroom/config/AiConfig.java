package com.tc.traumchatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置 — 从 application.yml 读取 ai.api.xxx 配置项
 * 用于调用 DeepSeek-V3 API
 */
@Configuration
@ConfigurationProperties(prefix = "ai.api")
@Data
public class AiConfig {
    /** API 地址，如 https://api.deepseek.com/v1/chat/completions */
    private String url;
    /** API Key */
    private String key;
    /** 模型名称，如 deepseek-chat */
    private String model;
    /** 最大 Token 数，如 1024 */
    private int maxTokens;
    /** 温度（0-1），越高回答越随机，0.7 比较平衡 */
    private double temperature;
    /** 超时时间（毫秒），如 30000 = 30秒 */
    private int timeout;
    /** 系统提示词，告诉 AI 它的角色和行为 */
    private String systemPrompt;
}
