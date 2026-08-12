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
}
