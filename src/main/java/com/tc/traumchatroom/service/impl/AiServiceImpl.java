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
import org.springframework.scheduling.annotation.Scheduled;
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

    /** AI 调用专用 WebClient（单例，见 AiClientConfig；连接池跨调用复用） */
    @Resource(name = "aiWebClient")
    private WebClient aiWebClient;

    // @小汤 触发词正则
    private static final Pattern AI_MENTION = Pattern.compile("@小汤|@AI|@ai|@Ai");

    /** 单会话保留的上下文条数（与原实现一致） */
    private static final int CONTEXT_KEEP_COUNT = 20;

    /** 陈旧上下文保留天数：超过则整条删除，可配置 */
    @org.springframework.beans.factory.annotation.Value("${ai.context.retention-days:30}")
    private int contextRetentionDays;

    /** 单次定时清理的删除上限，避免长事务 */
    @org.springframework.beans.factory.annotation.Value("${ai.context.cleanup-batch-size:5000}")
    private int contextCleanupBatchSize;

    /**
     * 定时清理陈旧 AI 上下文（每天凌晨 3:17）。
     *
     * deleteOld 只保证「每个 session_key 最多 20 条」，但 session_key 本身永不消失：
     * 格式 group:{username} 里含 2 小时即失效的游客名，表行数会随历史用户数单调增长。
     * 这里按 created_at 兜底清理，分批删除避免长事务。
     *
     * 时间点选 3:17 而非 3:00：整点是各类定时任务的高峰，错开可减少互相争抢。
     */
    @Scheduled(cron = "0 17 3 * * *")
    public void cleanupStaleContext() {
        try {
            java.time.LocalDateTime cutoff =
                    java.time.LocalDateTime.now().minusDays(contextRetentionDays);
            int total = 0;
            int deleted;
            // 分批删直到删不动，单轮上限防止极端情况下无限循环
            for (int round = 0; round < 100; round++) {
                deleted = aiContextMapper.deleteStaleBefore(cutoff, contextCleanupBatchSize);
                total += deleted;
                if (deleted < contextCleanupBatchSize) break;
            }
            if (total > 0) {
                log.info("清理陈旧 AI 上下文 {} 条（保留 {} 天）", total, contextRetentionDays);
            }
        } catch (Exception e) {
            log.warn("清理陈旧 AI 上下文失败", e);
        }
    }

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

            // 6. 调用 DeepSeek API（复用单例 WebClient，见 AiClientConfig）
            String responseBody = aiWebClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(aiConfig.getTimeout()))
                    .block();

            // 7. 解析响应
            String reply = parseReply(responseBody);

            // 7.1 硬性字数上限兜底：提示词可能被模型忽略或被提示注入越过，
            //     这里在服务端按 max-reply-chars 强制截断，保证回复绝不超长
            reply = truncateReply(reply);

            // 8. 保存上下文到数据库（两条一起写，裁剪只做一次，见 saveContext / trimContext）
            saveContext(sessionKey, "user", userMessage);
            saveContext(sessionKey, "assistant", reply);
            trimContext(sessionKey);

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
     * 保存一条对话上下文（不做裁剪 —— 裁剪由 trimContext 在一次交互结束后统一执行一次）
     */
    private void saveContext(String sessionKey, String role, String content) {
        AiConversationContext ctx = new AiConversationContext();
        ctx.setSessionKey(sessionKey);
        ctx.setRole(role);
        ctx.setContent(content);
        aiContextMapper.insert(ctx);
    }

    /**
     * 裁剪该会话的旧记录，只保留最近 CONTEXT_KEEP_COUNT 条。
     *
     * 原先写在 saveContext 里，一次 AI 交互（user + assistant 两条）就要跑两遍
     * DELETE ... WHERE id NOT IN (SELECT ... LIMIT ...)。裁剪的目的只是控制单会话行数，
     * 一次交互结束后做一次即可，语义无差别，DELETE 次数减半。
     */
    private void trimContext(String sessionKey) {
        try {
            aiContextMapper.deleteOld(sessionKey, CONTEXT_KEEP_COUNT);
        } catch (Exception e) {
            // 裁剪失败不影响本次回复，下次交互会再试
            log.warn("裁剪 AI 上下文失败: sessionKey={}", sessionKey, e);
        }
    }

    /**
     * 硬性字数上限兜底截断。
     * 提示词已要求 80 字以内，但模型可能超出（或被提示注入越过），
     * 这里按 max-reply-chars 强制截断：优先在上限附近的句末标点处优雅收尾，
     * 否则硬截断并追加省略号，确保回复绝不超长。
     */
    private String truncateReply(String reply) {
        if (reply == null) return "";
        int limit = aiConfig.getMaxReplyChars();
        // 未配置或非正数时不截断，保持向后兼容
        if (limit <= 0 || reply.length() <= limit) {
            return reply;
        }
        // 避免恰好在代理对（emoji 等）中间切断
        if (Character.isHighSurrogate(reply.charAt(limit - 1))) {
            limit -= 1;
        }
        String head = reply.substring(0, limit);
        // 在上限附近寻找最后一个句末标点，优雅收尾
        int cut = -1;
        for (String p : new String[]{"。", "！", "？", "…", ".", "!", "?", "；", ";", "\n"}) {
            int idx = head.lastIndexOf(p);
            if (idx > cut) cut = idx;
        }
        String result;
        // 句末标点出现在靠后位置（不小于上限一半）才在此收尾，否则硬截断加省略号
        if (cut >= limit / 2) {
            result = head.substring(0, cut + 1);
        } else {
            result = head.trim() + "…";
        }
        log.warn("AI 回复超出字数上限已截断：原始长度={}, 上限={}, 截断后长度={}",
                reply.length(), aiConfig.getMaxReplyChars(), result.length());
        return result;
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
