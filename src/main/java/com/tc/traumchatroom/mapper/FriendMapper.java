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
