package com.tc.traumchatroom.dto.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群聊 @提及 未读提醒项
 * 对应 GET /api/messages/mention-unread 返回的单个被@提醒
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MentionNoticeVO {
    /** 发送者用户名 */
    private String senderUsername;
    /** 发送者显示昵称 */
    private String senderName;
    /** 被@的消息ID（用于前端定位滚动） */
    private Long messageId;
    /** 消息内容摘要 */
    private String content;
    /** 发送时间 */
    private String createdAt;
}
