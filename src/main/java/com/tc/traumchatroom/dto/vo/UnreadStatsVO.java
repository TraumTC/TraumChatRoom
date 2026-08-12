package com.tc.traumchatroom.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读统计结果（Mapper 查询用内部载体）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnreadStatsVO {
    /** 未读消息数量 */
    private Integer unreadCount;
    /** 未读消息中最大的消息ID */
    private Long lastMessageId;
}
