package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.AiConversationContext;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI对话上下文Mapper接口
 */
public interface AiContextMapper {

    /** 插入上下文记录 */
    int insert(AiConversationContext context);

    /** 查询最近N条上下文（按时间倒序） */
    List<AiConversationContext> findRecent(@Param("sessionKey") String sessionKey,
                                           @Param("limit") int limit);

    /** 删除指定会话的旧记录（保留最近N条） */
    int deleteOld(@Param("sessionKey") String sessionKey,
                  @Param("keepCount") int keepCount);

    /** 删除指定会话的所有记录 */
    int deleteBySessionKey(@Param("sessionKey") String sessionKey);
}
