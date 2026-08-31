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

    /**
     * 删除早于 cutoff 的陈旧上下文（整个 session_key 维度的清理）。
     *
     * deleteOld 只保证「每个 session_key 最多 20 条」，但 session_key 本身永不消失：
     * 格式为 group:{username}，其中包含 2 小时即过期的游客名 ——
     * 表行数会随历史用户数单调增长。这里按时间兜底清理。
     *
     * @param cutoff    删除 created_at 早于该时刻的记录
     * @param batchSize 单次删除上限，避免一次删太多产生长事务与大量锁
     * @return 实际删除行数
     */
    int deleteStaleBefore(@Param("cutoff") java.time.LocalDateTime cutoff,
                          @Param("batchSize") int batchSize);
}
