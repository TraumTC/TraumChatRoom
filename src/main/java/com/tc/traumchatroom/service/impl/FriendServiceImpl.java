package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.dto.request.FriendApplyRequest;
import com.tc.traumchatroom.dto.request.FriendHandleRequest;
import com.tc.traumchatroom.dto.response.FriendRequestResponse;
import com.tc.traumchatroom.dto.response.FriendResponse;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.FriendRequestPageVO;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FriendServiceImpl implements FriendService {

    /** 好友申请有效期：30 天，超期视为过期 */
    private static final int REQUEST_EXPIRE_DAYS = 30;

    @Resource
    private FriendMapper friendMapper;

    @Resource
    private FriendRequestMapper friendRequestMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OnlineUserService onlineUserService;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    // ---------- 搜索用户 ----------

    @Override
    public List<FriendSearchVO> searchUsers(String keyword, String currentUsername) {
        User currentUser = userMapper.findByUsername(currentUsername);
        if (currentUser == null) return List.of();

        List<User> users = userMapper.searchUsers(keyword, currentUser.getId(), 20);

        // 过滤掉 AI 用户（小汤），不允许添加 AI 为好友
        users = users.stream()
                .filter(u -> !"ai_xiaoai".equals(u.getUsername()) && !"ROLE_AI".equals(u.getRole()))
                .collect(Collectors.toList());

        if (users.isEmpty()) return List.of();

        // 一次性取回好友关系与待处理申请，替代循环内的 1 次 exists + 2 次 findPending
        // （20 条结果原本最坏 60 次查询，现在固定 2 次）
        List<Integer> candidateIds = users.stream().map(User::getId).collect(Collectors.toList());
        Set<Integer> friendIds = new HashSet<>(
                friendMapper.findFriendIdsIn(currentUser.getId(), candidateIds));

        Set<Integer> pendingSentTo = new HashSet<>();      // 我发出的申请 → 对方 id
        Set<Integer> pendingReceivedFrom = new HashSet<>(); // 我收到的申请 → 对方 id
        for (FriendRequest fr : friendRequestMapper.findPendingBetween(currentUser.getId(), candidateIds)) {
            if (currentUser.getId().equals(fr.getSenderId())) {
                pendingSentTo.add(fr.getReceiverId());
            } else {
                pendingReceivedFrom.add(fr.getSenderId());
            }
        }

        return users.stream().map(user -> {
            FriendSearchVO vo = new FriendSearchVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setName(user.getName());
            vo.setAvatar(user.getAvatar());

            // 判断好友状态（优先级与原实现一致：friend > pending_sent > pending_received > none）
            if (friendIds.contains(user.getId())) {
                vo.setFriendStatus("friend");
            } else if (pendingSentTo.contains(user.getId())) {
                vo.setFriendStatus("pending_sent");
            } else if (pendingReceivedFrom.contains(user.getId())) {
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

        // 不能添加 AI 用户（小汤）为好友
        if ("ai_xiaoai".equals(receiver.getUsername()) || "ROLE_AI".equals(receiver.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能添加 AI 为好友");
        }

        // 不能添加自己
        if (sender.getId().equals(receiver.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        // 已经是好友
        if (friendMapper.exists(sender.getId(), receiver.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
        }

        // 已有待处理申请（双向：我发给对方，或对方发给我，避免互相 pending）
        FriendRequest existing = friendRequestMapper.findPendingBySenderAndReceiver(sender.getId(), receiver.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.REQUEST_EXISTS);
        }
        FriendRequest reverse = friendRequestMapper.findPendingBySenderAndReceiver(receiver.getId(), sender.getId());
        if (reverse != null) {
            throw new BusinessException(ErrorCode.REQUEST_EXISTS, "对方已向你发送好友申请，请前往处理");
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
    public FriendRequestPageVO getRequests(String username, String type, String status, int page, int size) {
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

        // 批量预取涉及的用户，避免每条申请 2 次 findById（N+1）
        Map<Integer, User> userMap = loadUsers(
                requests.stream()
                        .flatMap(fr -> java.util.stream.Stream.of(fr.getSenderId(), fr.getReceiverId()))
                        .collect(Collectors.toList()));

        List<FriendRequestResponse> items = requests.stream()
                .map(fr -> toFriendRequestResponse(fr, userMap))
                .collect(Collectors.toList());

        return new FriendRequestPageVO(items, (long) total, total > page * size);
    }

    /** 批量预取申请涉及的用户，避免 N+1 */
    private Map<Integer, User> loadUsers(Collection<Integer> ids) {
        List<Integer> distinct = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) return Map.of();
        return userMapper.findByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
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

        // 申请已过期（30 天）
        if (isExpired(fr)) {
            throw new BusinessException(ErrorCode.REQUEST_EXPIRED, "好友申请已过期，无法处理");
        }

        // 仅待处理状态可被处理（防重复 accept/reject）
        if (fr.getStatus() != null && fr.getStatus() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该申请已被处理");
        }

        if ("accept".equals(request.getAction())) {
            // 先确认申请人还在：findById 带 deleted_at IS NULL，申请人被管理员软删除后返回 null。
            // 原实现直到最后发通知时才 findById(...).getUsername() → NPE 500，
            // 而此时好友关系已经写进去了（事务回滚，但用户看到的是「失败」而状态语义不明）。
            // 提前校验既修掉 NPE，也避免给一个已注销账号建立好友关系。
            User senderUser = userMapper.findById(fr.getSenderId());
            if (senderUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "申请人账号已注销，无法同意该申请");
            }

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

            // WebSocket 通知申请方：申请已通过
            User receiverUser = userMapper.findById(fr.getReceiverId());
            messagingTemplate.convertAndSendToUser(
                    senderUser.getUsername(),
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
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的操作类型: " + request.getAction());
        }
    }

    /** 判断好友申请是否已过期（创建满 30 天） */
    private boolean isExpired(FriendRequest fr) {
        if (fr.getCreatedAt() == null) return false;
        return LocalDateTime.now().isAfter(fr.getCreatedAt().plusDays(REQUEST_EXPIRE_DAYS));
    }

    // ---------- 好友列表 ----------

    @Override
    public CursorPageVO<FriendResponse> getFriends(String username, String keyword, int page, int size) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        int offset = (page - 1) * size;

        List<Friend> friends = friendMapper.findByUserId(currentUser.getId(), keyword, offset, size);
        int total = friendMapper.countByUserId(currentUser.getId(), keyword);

        // 批量预取好友用户，避免每条好友一次 findById（N+1）
        Map<Integer, User> friendMap = loadUsers(
                friends.stream().map(Friend::getFriendId).collect(Collectors.toList()));

        // 一次取回在线用户集合，循环内内存判断，避免每个好友一次 ZSCORE 往返。
        // getOnlineUsers() 与 isOnline() 用的是同一个 5 分钟心跳窗口，判定结果等价。
        Set<String> onlineUsernames = onlineUserService.getOnlineUsers();

        List<FriendResponse> items = friends.stream().map(f -> {
            User friendUser = friendMap.get(f.getFriendId());
            if (friendUser == null) return null;

            FriendResponse resp = new FriendResponse();
            resp.setId(friendUser.getId());
            resp.setUsername(friendUser.getUsername());
            resp.setName(friendUser.getName());
            resp.setAvatar(friendUser.getAvatar());
            resp.setRemark(f.getRemark());
            resp.setOnline(onlineUsernames.contains(friendUser.getUsername()));
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

        log.info("用户 {} 删除了好友 {}", username, friendId);
    }

    // ---------- 删除好友申请记录 ----------

    @Override
    public void deleteRequest(Long requestId, String currentUsername) {
        User currentUser = userMapper.findByUsername(currentUsername);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        FriendRequest fr = friendRequestMapper.findById(requestId);
        if (fr == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申请记录不存在");
        }

        // 只有申请的发送者或接收者可以删除
        if (!fr.getSenderId().equals(currentUser.getId()) && !fr.getReceiverId().equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除此记录");
        }

        friendRequestMapper.deleteById(requestId);
        log.info("用户 {} 删除了好友申请记录 {}", currentUsername, requestId);
    }

    // ---------- 辅助方法 ----------

    private FriendRequestResponse toFriendRequestResponse(FriendRequest fr, Map<Integer, User> userMap) {
        FriendRequestResponse resp = new FriendRequestResponse();
        resp.setId(fr.getId());
        resp.setMessage(fr.getMessage());
        resp.setStatus(mapStatus(fr));
        resp.setCreatedAt(fr.getCreatedAt());

        // 发送者信息
        User sender = userMap.get(fr.getSenderId());
        if (sender != null) {
            resp.setSender(new FriendRequestResponse.SenderInfo(
                    sender.getId(), sender.getUsername(), sender.getName(), sender.getAvatar()));
        }

        // 接收者信息
        User receiver = userMap.get(fr.getReceiverId());
        if (receiver != null) {
            resp.setReceiver(new FriendRequestResponse.ReceiverInfo(
                    receiver.getId(), receiver.getUsername(), receiver.getName(), receiver.getAvatar()));
        }

        return resp;
    }

    private String mapStatus(FriendRequest fr) {
        Integer status = fr.getStatus();
        if (status == null) return "pending";
        return switch (status) {
            case 0 -> isExpired(fr) ? "expired" : "pending";
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
