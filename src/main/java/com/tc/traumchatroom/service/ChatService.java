package com.tc.traumchatroom.service;

import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.response.MessageResponse;

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
}
