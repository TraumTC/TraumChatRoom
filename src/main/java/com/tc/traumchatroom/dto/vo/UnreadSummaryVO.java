package com.tc.traumchatroom.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 私聊未读汇总项
 * 对应 GET /api/messages/unread-summary 返回的单个发送者未读统计
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnreadSummaryVO {
    /** 发送者用户ID */
    private Integer senderId;
    /** 发送者用户名 */
    private String senderUsername;
    /** 发送者显示昵称 */
    private String senderName;
    /** 未读消息数量 */
    private Integer unreadCount;
    /** 该会话最新的未读消息ID（用于前端防重复计数） */
    private Long lastMessageId;
}
