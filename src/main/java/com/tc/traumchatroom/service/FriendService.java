package com.tc.traumchatroom.service;

import com.tc.traumchatroom.dto.request.FriendApplyRequest;
import com.tc.traumchatroom.dto.request.FriendHandleRequest;
import com.tc.traumchatroom.dto.response.FriendRequestResponse;
import com.tc.traumchatroom.dto.response.FriendResponse;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.FriendRequestPageVO;
import com.tc.traumchatroom.dto.vo.FriendSearchVO;

import java.util.List;

/**
 * 好友服务接口
 */
public interface FriendService {

    /**
     * 搜索用户（添加好友前）
     */
    List<FriendSearchVO> searchUsers(String keyword, String currentUsername);

    /**
     * 发送好友申请
     */
    void sendRequest(String senderUsername, FriendApplyRequest request);

    /**
     * 获取好友申请列表
     * @param type received=收到的, sent=发出的
     * @param status 状态筛选（null=全部）
     */
    FriendRequestPageVO getRequests(String username, String type, String status, int page, int size);

    /**
     * 处理好友申请（同意/拒绝）
     */
    void handleRequest(Long requestId, String currentUsername, FriendHandleRequest request);

    /**
     * 获取好友列表
     */
    CursorPageVO<FriendResponse> getFriends(String username, String keyword, int page, int size);

    /**
     * 修改好友备注
     */
    void updateRemark(String username, Integer friendId, String remark);

    /**
     * 删除好友
     */
    void deleteFriend(String username, Integer friendId);

    /**
     * 删除好友申请记录
     */
    void deleteRequest(Long requestId, String currentUsername);
}
