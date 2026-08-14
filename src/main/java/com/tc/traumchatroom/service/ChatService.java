package com.tc.traumchatroom.service;

import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.dto.vo.MentionNoticeVO;
import com.tc.traumchatroom.dto.vo.UnreadSummaryVO;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 获取群聊历史消息（游标分页）
     * @param cursor 游标（消息ID），null 表示最新
     * @param size 每页数量
     * @return 分页消息列表
     */
    CursorPageVO<MessageResponse> getGroupHistory(Long cursor, int size);

    /**
     * 从指定消息ID开始向后获取群聊历史（含 anchor，用于@提及定位滚动）
     * @param anchorId 锚点消息ID
     * @param size 数量
     * @return 分页消息列表
     */
    CursorPageVO<MessageResponse> getGroupHistoryAround(Long anchorId, int size);

    /**
     * 获取私聊历史消息（游标分页）
     * @param currentUsername 当前用户名
     * @param targetUsername 目标用户名
     * @param cursor 游标
     * @param size 每页数量
     * @return 分页消息列表
     */
    CursorPageVO<MessageResponse> getPrivateHistory(String currentUsername, String targetUsername, Long cursor, int size);

    /**
     * 撤回消息
     * @param messageId 消息ID
     * @param currentUsername 当前用户名
     * @param currentRole 当前用户角色
     */
    void recallMessage(Long messageId, String currentUsername, String currentRole);

    /**
     * 获取私聊未读汇总（离线/未打开会话的消息，按发送者分组）
     * 未读 = id 大于该会话"已读游标"的消息；无游标的会话不返回（惰性初始化避免历史刷屏）
     * @param username 当前用户名
     * @return 未读汇总列表（仅含未读数 > 0 的会话）
     */
    List<UnreadSummaryVO> getUnreadSummary(String username);

    /**
     * 标记某私聊会话已读：将该会话双方最新消息ID写入 Redis 已读游标（TTL 90 天，活跃时刷新）
     * @param username 当前用户名
     * @param targetUsername 会话对象用户名
     */
    void markConversationRead(String username, String targetUsername);

    /**
     * 获取群聊 @提及未读提醒（Redis List，最新在前）
     * @param username 当前用户名
     * @return 未读提醒列表
     */
    List<MentionNoticeVO> getMentionUnread(String username);

    /**
     * 清除当前用户的全部群聊 @提及未读
     * @param username 当前用户名
     */
    void clearMentionUnread(String username);

    /**
     * 将当前用户的一条群聊 @提及标记为已读。
     * @param username 当前用户名
     * @param messageId 被 @消息 ID
     */
    void markMentionRead(String username, Long messageId);
}
