package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI对话上下文实体 — 对应 ai_conversation_context 表
 */
@Data
public class AiConversationContext {
    /** 记录ID */
    private Long id;
    /** 会话标识：group(群聊) / userA:userB(私聊) */
    private String sessionKey;
    /** 消息角色：user / assistant */
    private String role;
    /** 消息内容 */
    private String content;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
