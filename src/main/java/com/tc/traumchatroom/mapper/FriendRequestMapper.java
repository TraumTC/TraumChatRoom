package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.FriendRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友申请Mapper接口
 */
public interface FriendRequestMapper {

    /** 插入好友申请 */
    int insert(FriendRequest request);

    /** 更新申请状态（同意/拒绝） */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 根据ID查询申请 */
    FriendRequest findById(@Param("id") Long id);

    /** 查询收到的申请列表（分页） */
    List<FriendRequest> findByReceiverId(@Param("receiverId") Integer receiverId,
                                         @Param("status") Integer status,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /** 查询发出的申请列表（分页） */
    List<FriendRequest> findBySenderId(@Param("senderId") Integer senderId,
                                       @Param("status") Integer status,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 查询收到的申请总数 */
    int countByReceiverId(@Param("receiverId") Integer receiverId,
                          @Param("status") Integer status);

    /** 查询发出的申请总数 */
    int countBySenderId(@Param("senderId") Integer senderId,
                        @Param("status") Integer status);

    /** 查询是否存在待处理的申请 */
    FriendRequest findPendingBySenderAndReceiver(@Param("senderId") Integer senderId,
                                                  @Param("receiverId") Integer receiverId);

    /** 删除申请记录 */
    int deleteById(@Param("id") Long id);
}
