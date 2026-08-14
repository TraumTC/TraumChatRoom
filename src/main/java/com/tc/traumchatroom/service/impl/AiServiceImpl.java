package com.tc.traumchatroom.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.traumchatroom.config.AiConfig;
import com.tc.traumchatroom.config.AiRateLimitConfig;
import com.tc.traumchatroom.entity.AiConversationContext;
import com.tc.traumchatroom.mapper.AiContextMapper;
import com.tc.traumchatroom.service.AiService;
import com.tc.traumchatroom.util.RedisRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 服务实现
 * 调用 DeepSeek-V3 API 实现 @小汤 自动回复
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private AiConfig aiConfig;

    @Resource
    private AiRateLimitConfig aiRateLimitConfig;

    @Resource
    private AiContextMapper aiContextMapper;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RedisRateLimiter redisRateLimiter;

    // @小汤 触发词正则
    private static final Pattern AI_MENTION = Pattern.compile("@小汤|@AI|@ai|@Ai");

    @Override
    public boolean detectAiMention(String content) {
        return content != null && AI_MENTION.matcher(content).find();
    }

    @Override
    public String extractUserQuery(String content) {
        if (content == null) return "";
        return AI_MENTION.matcher(content).replaceAll("").trim();
    }

    @Override
    public String getAiReply(String userMessage, String sessionKey) {
        // 1. 限流检查（原子 Lua：计数 + TTL 一次性完成；上限读配置 ai.rate-limit.max-per-minute）
        String rateKey = "chat:ai:rate:" + sessionKey;
        int maxCallsPerMinute = aiRateLimitConfig.getMaxPerMinute();
        if (!redisRateLimiter.tryAcquire(rateKey, maxCallsPerMinute, 60)) {
            return "小汤正在思考中，请稍后再试~";
        }

        try {
            // 3. 获取最近上下文（最近 5 轮对话）
            List<Map<String, String>> contextMessages = getRecentContext(sessionKey, 5);

            // 4. 构造请求消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", aiConfig.getSystemPrompt()));
            messages.addAll(contextMessages);
            messages.add(Map.of("role", "user", "content", userMessage));

            // 5. 构造请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", aiConfig.getMaxTokens());
            requestBody.put("temperature", aiConfig.getTemperature());

            // 6. 调用 DeepSeek API
            WebClient webClient = WebClient.builder()
                    .baseUrl(aiConfig.getUrl())
                    .defaultHeader("Authorization", "Bearer " + aiConfig.getKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String responseBody = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(aiConfig.getTimeout()))
                    .block();

            // 7. 解析响应
            String reply = parseReply(responseBody);

            // 8. 保存上下文到数据库
            saveContext(sessionKey, "user", userMessage);
            saveContext(sessionKey, "assistant", reply);

            log.info("AI 回复成功，sessionKey={}, 问题长度={}, 回复长度={}",
                    sessionKey, userMessage.length(), reply.length());

            return reply;

        } catch (Exception e) {
            // 补异常对象：生产环境 AI 调用失败需堆栈定位（网络/超时/API 响应异常）
            log.error("AI 调用失败: {}", e.getMessage(), e);
            return "小汤暂时无法回复，请稍后再试~";
        }
    }

    /**
     * 获取最近 N 轮对话上下文
     */
    private List<Map<String, String>> getRecentContext(String sessionKey, int rounds) {
        List<AiConversationContext> contexts = aiContextMapper.findRecent(sessionKey, rounds * 2);
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiConversationContext ctx : contexts) {
            messages.add(Map.of("role", ctx.getRole(), "content", ctx.getContent()));
        }
        return messages;
    }

    /**
     * 保存对话上下文
     */
    private void saveContext(String sessionKey, String role, String content) {
        AiConversationContext ctx = new AiConversationContext();
        ctx.setSessionKey(sessionKey);
        ctx.setRole(role);
        ctx.setContent(content);
        aiContextMapper.insert(ctx);

        // 清理旧记录，只保留最近 20 条
        aiContextMapper.deleteOld(sessionKey, 20);
    }

    /**
     * 解析 DeepSeek API 响应
     * 响应格式：{"choices":[{"message":{"content":"回复内容"}}]}
     */
    private String parseReply(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText().trim();
                    }
                }
            }
            return "小汤暂时无法理解您的问题~";
        } catch (Exception e) {
            log.error("解析 AI 响应失败", e);
            return "小汤暂时无法回复，请稍后再试~";
        }
    }
}
