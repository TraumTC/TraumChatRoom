package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.Friend;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友关系Mapper接口
 */
public interface FriendMapper {

    /** 插入好友关系 */
    int insert(Friend friend);

    /** 删除好友关系（双向删除） */
    int delete(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /** 查询是否存在好友关系 */
    boolean exists(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 批量判断好友关系：从候选 id 中筛出与 userId 已是好友的那些。
     * 与 {@link #exists} 同为双向判定，用于替代「循环内逐个 exists」。
     * 调用方需保证 candidateIds 非空（空集合会生成非法的 IN ()）。
     */
    List<Integer> findFriendIdsIn(@Param("userId") Integer userId,
                                  @Param("candidateIds") List<Integer> candidateIds);

    /** 查询用户的好友列表（分页） */
    List<Friend> findByUserId(@Param("userId") Integer userId,
                              @Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("size") int size);

    /** 查询用户的好友总数 */
    int countByUserId(@Param("userId") Integer userId,
                      @Param("keyword") String keyword);

    /** 修改好友备注 */
    int updateRemark(@Param("userId") Integer userId,
                     @Param("friendId") Integer friendId,
                     @Param("remark") String remark);

    /** 查询好友备注 */
    String findRemark(@Param("userId") Integer userId,
                      @Param("friendId") Integer friendId);
}
