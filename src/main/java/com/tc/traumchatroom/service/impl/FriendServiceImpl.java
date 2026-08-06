package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.dto.request.FriendApplyRequest;
import com.tc.traumchatroom.dto.request.FriendHandleRequest;
import com.tc.traumchatroom.dto.response.FriendRequestResponse;
import com.tc.traumchatroom.dto.response.FriendResponse;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.FriendSearchVO;
import com.tc.traumchatroom.entity.Friend;
import com.tc.traumchatroom.entity.FriendRequest;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.FriendMapper;
import com.tc.traumchatroom.mapper.FriendRequestMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.FriendService;
import com.tc.traumchatroom.service.OnlineUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FriendServiceImpl implements FriendService {

    @Resource
    private FriendMapper friendMapper;

    @Resource
    private FriendRequestMapper friendRequestMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OnlineUserService onlineUserService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    // ---------- 搜索用户 ----------

    @Override
    public List<FriendSearchVO> searchUsers(String keyword, String currentUsername) {
        User currentUser = userMapper.findByUsername(currentUsername);
        if (currentUser == null) return List.of();

        List<User> users = userMapper.searchUsers(keyword, currentUser.getId(), 20);

        return users.stream().map(user -> {
            FriendSearchVO vo = new FriendSearchVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setName(user.getName());
            vo.setAvatar(user.getAvatar());

            // 判断好友状态
            if (friendMapper.exists(currentUser.getId(), user.getId())) {
                vo.setFriendStatus("friend");
            } else if (friendRequestMapper.findPendingBySenderAndReceiver(currentUser.getId(), user.getId()) != null) {
                vo.setFriendStatus("pending_sent");
            } else if (friendRequestMapper.findPendingBySenderAndReceiver(user.getId(), currentUser.getId()) != null) {
                vo.setFriendStatus("pending_received");
            } else {
                vo.setFriendStatus("none");
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ---------- 发送好友申请 ----------

    @Override
    public void sendRequest(String senderUsername, FriendApplyRequest request) {
        User sender = userMapper.findByUsername(senderUsername);
        User receiver = userMapper.findById(request.getReceiverId());

        if (sender == null || receiver == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 不能添加自己
        if (sender.getId().equals(receiver.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        // 已经是好友
        if (friendMapper.exists(sender.getId(), receiver.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
        }

        // 已有待处理申请
        FriendRequest existing = friendRequestMapper.findPendingBySenderAndReceiver(sender.getId(), receiver.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.REQUEST_EXISTS);
        }

        // 创建申请
        FriendRequest fr = new FriendRequest();
        fr.setSenderId(sender.getId());
        fr.setReceiverId(receiver.getId());
        fr.setMessage(request.getMessage());
        fr.setStatus(0);  // 待处理
        friendRequestMapper.insert(fr);

        // WebSocket 通知接收方
        messagingTemplate.convertAndSendToUser(
                receiver.getUsername(),
                "/queue/friend-request",
                Map.of(
                        "type", "friend_request",
                        "requestId", fr.getId(),
                        "sender", Map.of(
                                "id", sender.getId(),
                                "username", sender.getUsername(),
                                "name", sender.getName(),
                                "avatar", sender.getAvatar() != null ? sender.getAvatar() : ""
                        ),
                        "message", request.getMessage() != null ? request.getMessage() : "",
                        "createdAt", LocalDateTime.now().toString()
                )
        );

        log.info("用户 {} 向 {} 发送好友申请", senderUsername, receiver.getUsername());
    }

    // ---------- 获取申请列表 ----------

    @Override
    public CursorPageVO<FriendRequestResponse> getRequests(String username, String type, String status, int page, int size) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        int offset = (page - 1) * size;
        Integer statusInt = parseStatus(status);

        List<FriendRequest> requests;
        int total;

        if ("sent".equals(type)) {
            requests = friendRequestMapper.findBySenderId(currentUser.getId(), statusInt, offset, size);
            total = friendRequestMapper.countBySenderId(currentUser.getId(), statusInt);
        } else {
            requests = friendRequestMapper.findByReceiverId(currentUser.getId(), statusInt, offset, size);
            total = friendRequestMapper.countByReceiverId(currentUser.getId(), statusInt);
        }

        List<FriendRequestResponse> items = requests.stream()
                .map(this::toFriendRequestResponse)
                .collect(Collectors.toList());

        return new CursorPageVO<>(items, null, total > page * size);
    }

    // ---------- 处理申请 ----------

    @Override
    @Transactional
    public void handleRequest(Long requestId, String currentUsername, FriendHandleRequest request) {
        User currentUser = userMapper.findByUsername(currentUsername);
        FriendRequest fr = friendRequestMapper.findById(requestId);

        if (fr == null) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_FOUND);
        }

        // 只有接收者能处理
        if (!fr.getReceiverId().equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能处理发给自己的申请");
        }

        if ("accept".equals(request.getAction())) {
            // 同意：更新状态 + 创建双向好友关系
            friendRequestMapper.updateStatus(requestId, 1);

            // 插入两条好友记录
            Friend f1 = new Friend();
            f1.setUserId(fr.getSenderId());
            f1.setFriendId(fr.getReceiverId());
            friendMapper.insert(f1);

            Friend f2 = new Friend();
            f2.setUserId(fr.getReceiverId());
            f2.setFriendId(fr.getSenderId());
            friendMapper.insert(f2);

            // 清除双方好友列表缓存
            redisTemplate.delete("chat:friends:" + fr.getSenderId());
            redisTemplate.delete("chat:friends:" + fr.getReceiverId());

            // WebSocket 通知申请方：申请已通过
            User receiverUser = userMapper.findById(fr.getReceiverId());
            messagingTemplate.convertAndSendToUser(
                    userMapper.findById(fr.getSenderId()).getUsername(),
                    "/queue/friend-accepted",
                    Map.of(
                            "type", "friend_accepted",
                            "friend", Map.of(
                                    "id", receiverUser.getId(),
                                    "username", receiverUser.getUsername(),
                                    "name", receiverUser.getName(),
                                    "avatar", receiverUser.getAvatar() != null ? receiverUser.getAvatar() : ""
                            ),
                            "createdAt", LocalDateTime.now().toString()
                    )
            );

            log.info("用户 {} 同意了 {} 的好友申请", currentUsername, fr.getSenderId());

        } else if ("reject".equals(request.getAction())) {
            friendRequestMapper.updateStatus(requestId, 2);
            log.info("用户 {} 拒绝了 {} 的好友申请", currentUsername, fr.getSenderId());
        }
    }

    // ---------- 好友列表 ----------

    @Override
    public CursorPageVO<FriendResponse> getFriends(String username, String keyword, int page, int size) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        int offset = (page - 1) * size;

        List<Friend> friends = friendMapper.findByUserId(currentUser.getId(), keyword, offset, size);
        int total = friendMapper.countByUserId(currentUser.getId(), keyword);

        List<FriendResponse> items = friends.stream().map(f -> {
            User friendUser = userMapper.findById(f.getFriendId());
            if (friendUser == null) return null;

            FriendResponse resp = new FriendResponse();
            resp.setId(friendUser.getId());
            resp.setUsername(friendUser.getUsername());
            resp.setName(friendUser.getName());
            resp.setAvatar(friendUser.getAvatar());
            resp.setRemark(f.getRemark());
            resp.setOnline(onlineUserService.isOnline(friendUser.getUsername()));
            resp.setLastActiveTime(friendUser.getLastActiveTime());
            return resp;
        }).filter(r -> r != null).collect(Collectors.toList());

        return new CursorPageVO<>(items, null, total > page * size);
    }

    // ---------- 修改备注 ----------

    @Override
    public void updateRemark(String username, Integer friendId, String remark) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        // 检查是否是好友
        if (!friendMapper.exists(currentUser.getId(), friendId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对方不是你的好友");
        }

        friendMapper.updateRemark(currentUser.getId(), friendId, remark);
    }

    // ---------- 删除好友 ----------

    @Override
    @Transactional
    public void deleteFriend(String username, Integer friendId) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        // 双向删除（一条 SQL 删两条：A→B 与 B→A）
        friendMapper.delete(currentUser.getId(), friendId);

        // 清除双方好友列表缓存
        redisTemplate.delete("chat:friends:" + currentUser.getId());
        redisTemplate.delete("chat:friends:" + friendId);

        log.info("用户 {} 删除了好友 {}", username, friendId);
    }

    // ---------- 辅助方法 ----------

    private FriendRequestResponse toFriendRequestResponse(FriendRequest fr) {
        FriendRequestResponse resp = new FriendRequestResponse();
        resp.setId(fr.getId());
        resp.setMessage(fr.getMessage());
        resp.setStatus(mapStatus(fr.getStatus()));
        resp.setCreatedAt(fr.getCreatedAt());

        // 发送者信息
        User sender = userMapper.findById(fr.getSenderId());
        if (sender != null) {
            resp.setSender(new FriendRequestResponse.SenderInfo(
                    sender.getId(), sender.getUsername(), sender.getName(), sender.getAvatar()));
        }

        // 接收者信息
        User receiver = userMapper.findById(fr.getReceiverId());
        if (receiver != null) {
            resp.setReceiver(new FriendRequestResponse.ReceiverInfo(
                    receiver.getId(), receiver.getUsername(), receiver.getName(), receiver.getAvatar()));
        }

        return resp;
    }

    private String mapStatus(Integer status) {
        if (status == null) return "pending";
        return switch (status) {
            case 0 -> "pending";
            case 1 -> "accepted";
            case 2 -> "rejected";
            default -> "unknown";
        };
    }

    private Integer parseStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "pending" -> 0;
            case "accepted" -> 1;
            case "rejected" -> 2;
            default -> null;
        };
    }
}
