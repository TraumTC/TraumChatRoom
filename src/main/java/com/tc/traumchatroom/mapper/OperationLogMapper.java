package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志Mapper接口
 */
public interface OperationLogMapper {

    /** 插入操作日志 */
    int insert(OperationLog log);

    /** 查询操作日志（分页，支持按操作类型和时间范围筛选） */
    List<OperationLog> findByConditions(@Param("action") String action,
                                        @Param("startDate") String startDate,
                                        @Param("endDate") String endDate,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /** 查询操作日志总数 */
    int countByConditions(@Param("action") String action,
                          @Param("startDate") String startDate,
                          @Param("endDate") String endDate);

    /** 根据ID查询 */
    OperationLog findById(@Param("id") Long id);

    /** 根据用户ID查询（分页） */
    List<OperationLog> findByUserId(@Param("userId") Integer userId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);
}
