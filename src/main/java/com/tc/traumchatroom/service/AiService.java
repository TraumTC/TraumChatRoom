package com.tc.traumchatroom.service;

/**
 * AI 服务接口
 */
public interface AiService {

    /**
     * 检测消息是否包含 @小爱 触发词
     * @param content 消息内容
     * @return true=包含触发词
     */
    boolean detectAiMention(String content);

    /**
     * 提取用户问题（去掉 @小爱 前缀）
     * @param content 原始消息
     * @return 用户问题
     */
    String extractUserQuery(String content);

    /**
     * 异步获取 AI 回复
     * @param userMessage 用户消息
     * @param sessionKey 会话标识（group 或 私聊的 userA:userB）
     * @return AI 回复内容
     */
    String getAiReply(String userMessage, String sessionKey);
}
