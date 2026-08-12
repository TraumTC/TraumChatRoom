package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.UnreadStatsVO;
import com.tc.traumchatroom.dto.vo.UnreadSummaryVO;
import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.CacheService;
import com.tc.traumchatroom.service.ChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CacheService cacheService;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private com.tc.traumchatroom.mapper.FriendMapper friendMapper;

    @Resource
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    /** 撤回时间窗（秒），可配置，默认 120 秒 */
    @org.springframework.beans.factory.annotation.Value("${chat.recall-window-seconds:120}")
    private long recallWindowSeconds;

    /** 私聊已读游标 Redis Hash 前缀：chat:read:{username} → {peerUsername: lastMessageId} */
    private static final String READ_KEY_PREFIX = "chat:read:";
    /** 已读游标 TTL：90 天（活跃时刷新，长期不活跃自动清理，避免无效数据残留） */
    private static final long READ_TTL_DAYS = 90;

    // ---------- 群聊历史 ----------

    @Override
    public CursorPageVO<MessageResponse> getGroupHistory(Long cursor, int size) {
        // 限制每页最大 100 条
        if (size <= 0 || size > 100) size = 20;

        // 查询消息列表（多查一条用于判断 hasMore）
        List<Message> messages = messageMapper.selectGroupHistory(cursor, size + 1);

        boolean hasMore = messages.size() > size;
        if (hasMore) {
            messages = messages.subList(0, size); // 截取 size 条
        }

        // 转换为响应 DTO
        List<MessageResponse> items = messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        // 计算下一页游标
        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).getId();

        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    // ---------- 私聊历史 ----------

    @Override
    public CursorPageVO<MessageResponse> getPrivateHistory(String currentUsername, String targetUsername, Long cursor, int size) {
        if (size <= 0 || size > 100) size = 20;

        // 获取当前用户和目标用户信息
        User currentUser = userMapper.findByUsername(currentUsername);
        User targetUser = userMapper.findByUsername(targetUsername);

        if (currentUser == null || targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 检查游客权限
        if ("ROLE_GUEST".equals(currentUser.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "游客不能查看私聊记录");
        }

        // 私聊历史仅限好友关系（防止任意用户窥探他人会话）
        if (!friendMapper.exists(currentUser.getId(), targetUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看好友的私聊记录");
        }

        // 查询私聊消息
        List<Message> messages = messageMapper.selectPrivateHistory(
                currentUser.getId(), currentUsername,
                targetUser.getId(), targetUsername,
                cursor, size + 1
        );

        boolean hasMore = messages.size() > size;
        if (hasMore) {
            messages = messages.subList(0, size);
        }

        List<MessageResponse> items = messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).getId();

        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    // ---------- 撤回消息 ----------

    @Override
    @Transactional
    public void recallMessage(Long messageId, String currentUsername, String currentRole) {
        // 1. 查询消息
        Message message = messageMapper.findById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }

        // 2. 已撤回的不能再撤回
        if (message.getIsRecalled() != null && message.getIsRecalled() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息已被撤回");
        }

        // 3. 权限检查：本人（含游客）或管理员
        User currentUser = userMapper.findByUsername(currentUsername);
        boolean isAdmin = "ROLE_ADMIN".equals(currentRole);
        boolean isOwner = currentUser != null && message.getSenderId() != null
                && message.getSenderId().equals(currentUser.getId());
        if (!isOwner && !isAdmin) {
            // 游客本人：sender_id 为 null，按 Redis 游客显示名匹配 sender_name
            if (currentUsername.startsWith("guest_")) {
                String guestKey = "chat:guest:" + currentUsername;
                Object guestName = redisTemplate.opsForHash().get(guestKey, "name");
                if (guestName != null && guestName.toString().equals(message.getSenderName())) {
                    isOwner = true;
                }
            }
            if (!isOwner) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤回自己的消息");
            }
        }

        // 4. 时间检查：配置的撤回窗口内
        if (message.getCreatedAt() != null) {
            LocalDateTime deadline = message.getCreatedAt().plusSeconds(recallWindowSeconds);
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new BusinessException(ErrorCode.RECALL_TIMEOUT);
            }
        }

        // 5. 执行撤回
        String recalledContent = message.getSenderName() + " 撤回了一条消息";
        messageMapper.updateRecall(messageId, recalledContent, message.getContent());

        // 6. 广播撤回通知（群聊广播到 /topic/messages，私聊推送给双方）
        Object recallNotice = Map.of(
                "type", "message_recalled",
                "messageId", messageId,
                "senderName", message.getSenderName(),
                "recalledAt", LocalDateTime.now().toString()
        );

        if (message.getReceiverId() != null) {
            // 私聊：推送给接收者和发送者
            messagingTemplate.convertAndSendToUser(message.getReceiverName(), "/queue/message-recalled", recallNotice);
            messagingTemplate.convertAndSendToUser(currentUsername, "/queue/message-recalled", recallNotice);
        } else {
            // 群聊：广播给所有人
            messagingTemplate.convertAndSend("/topic/messages", recallNotice);
        }

        log.info("用户 {} 撤回消息 {}", currentUsername, messageId);
    }

    // ---------- 私聊离线未读（Redis 会话级已读游标） ----------

    @Override
    public List<UnreadSummaryVO> getUnreadSummary(String username) {
        User currentUser = userMapper.findByUsername(username);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        List<UnreadSummaryVO> result = new ArrayList<>();
        try {
            List<Integer> peerIds = messageMapper.selectPrivateConversationPeers(currentUser.getId());
            if (peerIds.isEmpty()) return result;

            Map<Integer, User> userMap = userMapper.findByIds(peerIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            String key = READ_KEY_PREFIX + username;
            Map<Object, Object> readMap = redisTemplate.opsForHash().entries(key);

            for (Integer peerId : peerIds) {
                User peer = userMap.get(peerId);
                if (peer == null) continue;

                Long lastReadId = parseReadId(readMap.get(peer.getUsername()));
                if (lastReadId == null) {
                    // 无游标（首次使用/已过期）：惰性初始化为当前最新消息ID，避免历史消息刷屏
                    Long latestId = messageMapper.selectConversationLatestId(currentUser.getId(), peerId);
                    if (latestId != null) {
                        redisTemplate.opsForHash().put(key, peer.getUsername(), String.valueOf(latestId));
                        redisTemplate.expire(key, Duration.ofDays(READ_TTL_DAYS));
                    }
                    continue;
                }

                UnreadStatsVO stats = messageMapper.selectUnreadStats(currentUser.getId(), peerId, lastReadId);
                if (stats != null && stats.getUnreadCount() != null && stats.getUnreadCount() > 0) {
                    UnreadSummaryVO vo = new UnreadSummaryVO();
                    vo.setSenderId(peer.getId());
                    vo.setSenderUsername(peer.getUsername());
                    vo.setSenderName(peer.getName());
                    vo.setUnreadCount(stats.getUnreadCount());
                    vo.setLastMessageId(stats.getLastMessageId());
                    result.add(vo);
                }
            }

            // 未读会话按最新消息ID倒序（新的在前）
            result.sort(Comparator.comparing(UnreadSummaryVO::getLastMessageId,
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        } catch (Exception e) {
            // Redis 异常降级：返回空，不阻塞聊天
            log.warn("获取私聊未读汇总失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void markConversationRead(String username, String targetUsername) {
        User currentUser = userMapper.findByUsername(username);
        User targetUser = userMapper.findByUsername(targetUsername);
        if (currentUser == null || targetUser == null) return;

        try {
            Long latestId = messageMapper.selectConversationLatestId(currentUser.getId(), targetUser.getId());
            if (latestId == null) return;
            String key = READ_KEY_PREFIX + username;
            redisTemplate.opsForHash().put(key, targetUsername, String.valueOf(latestId));
            redisTemplate.expire(key, Duration.ofDays(READ_TTL_DAYS));
        } catch (Exception e) {
            // Redis 异常忽略：仅影响下次上线的未读游标，不阻塞打开会话
            log.warn("标记会话已读失败: username={}, target={}", username, targetUsername);
        }
    }

    /** 解析 Redis 游标值（null/空/非法 → null） */
    private Long parseReadId(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- 辅助方法：实体 → 响应 DTO ----------

    private MessageResponse toMessageResponse(Message msg) {
        MessageResponse response = new MessageResponse();
        response.setId(msg.getId());
        response.setContent(msg.getContent());
        response.setMessageType(msg.getMessageType());
        response.setFileName(msg.getFileName());
        response.setFilePath(msg.getFilePath());
        response.setFileSize(msg.getFileSize());
        response.setAiReply(msg.getIsAiReply() != null && msg.getIsAiReply() == 1);
        response.setRecalled(msg.getIsRecalled() != null && msg.getIsRecalled() == 1);
        response.setReplyToId(msg.getReplyToId());
        response.setCreatedAt(msg.getCreatedAt());

        // 构造发送者信息（username/name 来自 JOIN user 表，改昵称后实时生效）
        MessageResponse.SenderInfo senderInfo = new MessageResponse.SenderInfo();
        senderInfo.setId(msg.getSenderId());
        senderInfo.setUsername(msg.getSenderUsername());
        senderInfo.setName(msg.getSenderName());
        // 查询发送者头像（走缓存，未命中回源数据库并回填）
        if (msg.getSenderId() != null) {
            User sender = cacheService.getUserById(msg.getSenderId());
            if (sender != null) {
                senderInfo.setAvatar(sender.getAvatar());
            }
        }
        response.setSender(senderInfo);

        // 构造接收者信息（私聊时，receiver_name 语义为 username）
        if (msg.getReceiverId() != null) {
            MessageResponse.ReceiverInfo receiverInfo = new MessageResponse.ReceiverInfo();
            receiverInfo.setId(msg.getReceiverId());
            receiverInfo.setUsername(msg.getReceiverName());
            receiverInfo.setName(msg.getReceiverName());
            response.setReceiver(receiverInfo);
        }

        return response;
    }
}
