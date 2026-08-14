package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper接口
 */
public interface MessageMapper {

    /** 根据ID查询消息 */
    Message findById(@Param("id") Long id);

    /** 插入消息 */
    int insert(Message message);

    /** 群聊历史（游标分页） */
    List<Message> selectGroupHistory(@Param("cursor") Long cursor,
                                     @Param("size") int size);

    /** 群聊历史（从指定消息ID开始向后取 size 条，含 anchor，用于@提及定位） */
    List<Message> selectGroupHistoryAround(@Param("anchorId") Long anchorId,
                                           @Param("size") int size);

    /** 私聊历史（游标分页） */
    List<Message> selectPrivateHistory(@Param("userId") Integer userId,
                                       @Param("senderName") String senderName,
                                       @Param("targetUserId") Integer targetUserId,
                                       @Param("targetName") String targetName,
                                       @Param("cursor") Long cursor,
                                       @Param("size") int size);

    /** 撤回消息 */
    int updateRecall(@Param("id") Long id,
                     @Param("content") String content,
                     @Param("originalContent") String originalContent);

    /** 软删除消息 */
    int softDelete(@Param("id") Long id);

    /** 同步更新消息表中的发送者昵称（用户改昵称时调用，按 sender_id 精确定位避免重名误伤） */
    int updateSenderName(@Param("senderId") Integer senderId,
                         @Param("oldName") String oldName,
                         @Param("newName") String newName);

    /** 查询最近的群聊消息（用于缓存） */
    List<Message> findRecentGroupMessages(@Param("limit") int limit);

    /** 查询当前用户所有私聊发送者ID（作为接收者的 sender 去重，用于离线未读统计） */
    List<Integer> selectPrivateConversationPeers(@Param("userId") Integer userId);

    /** 查询双方会话的最新消息ID（双向，用于初始化/推进已读游标） */
    Long selectConversationLatestId(@Param("userId") Integer userId,
                                    @Param("peerId") Integer peerId);

    /** 查询某会话中 id 大于已读游标的未读消息统计（数量 + 最新消息ID） */
    com.tc.traumchatroom.dto.vo.UnreadStatsVO selectUnreadStats(@Param("userId") Integer userId,
                                                                 @Param("peerId") Integer peerId,
                                                                 @Param("lastReadId") Long lastReadId);
}
