package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.SensitiveWord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 敏感词Mapper接口
 */
public interface SensitiveWordMapper {

    /** 查询所有敏感词 */
    List<SensitiveWord> findAll();

    /** 根据级别查询敏感词 */
    List<SensitiveWord> findByLevel(@Param("level") Integer level);

    /** 根据分类查询敏感词 */
    List<SensitiveWord> findByCategory(@Param("category") String category);

    /** 敏感词列表（管理员分页） */
    List<SensitiveWord> findByConditions(@Param("level") Integer level,
                                         @Param("category") String category,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /** 敏感词总数 */
    int countByConditions(@Param("level") Integer level,
                          @Param("category") String category);

    /** 根据ID查询 */
    SensitiveWord findById(@Param("id") Integer id);

    /** 插入敏感词 */
    int insert(SensitiveWord word);

    /** 删除敏感词 */
    int deleteById(@Param("id") Integer id);
}
