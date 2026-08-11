package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.OnlineUserInfo;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.AiService;
import com.tc.traumchatroom.service.FilterResult;
import com.tc.traumchatroom.service.OnlineUserService;
import com.tc.traumchatroom.service.SensitiveWordFilter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 消息控制器
 *
 * 处理客户端通过 STOMP 发送的消息：
 * - /app/space → 群聊消息
 * - /app/private.message → 私聊消息
 * - /app/heartbeat → 心跳
 * - /app/sync-state → 同步在线状态
 */
@Slf4j
@Controller
public class WebSocketChatController {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OnlineUserService onlineUserService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private AiService aiService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private com.tc.traumchatroom.mapper.FriendMapper friendMapper;

    /** AI 回复异步线程池（见 AsyncConfig.aiTaskExecutor） */
    @Resource(name = "aiTaskExecutor")
    private java.util.concurrent.Executor aiTaskExecutor;

    /** 消息内容最大长度（防超长消息 DoS） */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /** 消息发送限流：每用户每分钟最多 30 条（防灌水 DoS） */
    private static final int SEND_RATE_LIMIT = 30;
    private static final String SEND_RATE_LUA =
            "local cur = redis.call('GET', KEYS[1]) " +
            "if cur == false then " +
            "  redis.call('SET', KEYS[1], 1, 'EX', 60) " +
            "  return 1 " +
            "end " +
            "local n = redis.call('INCR', KEYS[1]) " +
            "return n";

    /**
     * 消息发送限流（Redis 原子计数，每用户每分钟 30 条）
     * @return true 允许发送；false 超限
     */
    private boolean allowSend(String username) {
        String key = "chat:send:rate:" + username;
        Long n = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(SEND_RATE_LUA, Long.class),
                java.util.List.of(key));
        long count = n != null ? n : 1;
        if (count > SEND_RATE_LIMIT) {
            log.warn("用户 {} 发送消息超限，拦截", username);
            return false;
        }
        return true;
    }

    /**
     * 发送群聊消息
     * 客户端发送到 /app/space
     * 服务端广播到 /topic/messages
     */
    @MessageMapping("/space")
    public void sendGroupMessage(Map<String, String> payload, Principal principal,
                                 org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor) {
        String content = payload.get("content");
        String username = principal.getName();
        String clientId = payload.get("clientId");

        // 发送频率限流（每用户每分钟 30 条）
        if (!allowSend(username)) {
            messagingTemplate.convertAndSendToUser(
                    username, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息发送过于频繁，请稍后再试")
            );
            return;
        }

        // 消息长度校验（空消息 / 超长消息直接拒绝）
        if (content == null || content.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    username, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息不能为空")
            );
            return;
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            messagingTemplate.convertAndSendToUser(
                    username, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 字")
            );
            return;
        }

        // 查询发送者信息（先查数据库，再查 Redis 游客）
        User sender = userMapper.findByUsername(username);
        if (sender == null && username.startsWith("guest_")) {
            // 游客从 Redis 获取信息
            String guestKey = "chat:guest:" + username;
            java.util.Map<Object, Object> guestData = redisTemplate.opsForHash().entries(guestKey);
            if (!guestData.isEmpty()) {
                sender = new User();
                sender.setUsername((String) guestData.get("username"));
                sender.setName((String) guestData.get("name"));
                sender.setRole("ROLE_GUEST");
            }
        }
        if (sender == null) return;

        // 敏感词过滤
        FilterResult filterResult = sensitiveWordFilter.filter(content);
        if (filterResult.isBlocked()) {
            // 发送错误通知给发送者
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/send-error",
                    Map.of("type", "send_error", "subtype", "blocked", "message", filterResult.getMessage())
            );
            return;
        }
        if (filterResult.isReplaced()) {
            content = filterResult.getContent();
        }

        // 消息幂等：同一 clientId 只处理一次，防重连/双击重复发送（校验通过后占用）
        if (!acquireMessageIdempotent(username, clientId)) {
            return;
        }

        // 构造消息实体
        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setSenderName(sender.getName());
        message.setContent(content);
        message.setMessageType("text");
        message.setIsAiReply(0);
        message.setIsRecalled(0);
        message.setSenderIp(resolveClientIp(accessor));

        // 引用回复
        String replyToIdStr = payload.get("replyToId");
        if (replyToIdStr != null && !replyToIdStr.isEmpty() && !"null".equals(replyToIdStr)) {
            try {
                message.setReplyToId(Long.parseLong(replyToIdStr));
            } catch (NumberFormatException ignored) {}
        }

        // 保存到数据库
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);

        // 构造响应对象
        MessageResponse response = toMessageResponse(message, sender);

        // 广播到群聊
        messagingTemplate.convertAndSend("/topic/messages", response);

        log.debug("群聊消息: {} -> {}", sender.getName(), content);

        // 检测 @小爱，触发 AI 回复（游客无权使用 AI）
        if (!"ROLE_GUEST".equals(sender.getRole()) && aiService.detectAiMention(content)) {
            triggerAiReply(content, message.getId(), sender.getUsername());
        }
    }

    /**
     * 异步触发 AI 回复（不阻塞消息发送）
     */
    private void triggerAiReply(String content, Long replyToId, String senderUsername) {
        // 使用独立线程池异步执行，避免阻塞 WebSocket 消息处理
        aiTaskExecutor.execute(() -> {
            try {
                String userQuery = aiService.extractUserQuery(content);
                // 会话 key 按用户隔离：限流配额与上下文互不共享，防止隐私串扰
                String aiReply = aiService.getAiReply(userQuery, "group:" + senderUsername);

                // 构造 AI 消息
                User aiUser = userMapper.findByUsername("ai_xiaoai");
                if (aiUser == null) {
                    // 并发首触发时用 INSERT IGNORE 幂等创建，避免唯一键冲突
                    aiUser = new User();
                    aiUser.setUsername("ai_xiaoai");
                    aiUser.setName("小爱");
                    aiUser.setPassword("ai-no-password");
                    aiUser.setRole("ROLE_AI");
                    aiUser.setStatus(1);
                    userMapper.insertIgnore(aiUser);
                    aiUser = userMapper.findByUsername("ai_xiaoai");
                    if (aiUser == null) {
                        log.error("AI 用户创建失败，跳过 AI 回复");
                        return;
                    }
                }

                Message aiMessage = new Message();
                aiMessage.setSenderId(aiUser.getId());
                aiMessage.setSenderName("小爱");
                aiMessage.setContent(aiReply);
                aiMessage.setMessageType("text");
                aiMessage.setIsAiReply(1);
                aiMessage.setIsRecalled(0);
                aiMessage.setReplyToId(replyToId);

                messageMapper.insert(aiMessage);

                MessageResponse aiResponse = toMessageResponse(aiMessage, aiUser);
                messagingTemplate.convertAndSend("/topic/messages", aiResponse);

                log.info("AI 回复: {}", aiReply.substring(0, Math.min(50, aiReply.length())));

            } catch (Exception e) {
                log.error("AI 回复失败", e);
            }
        });
    }

    /**
     * 发送私聊消息
     * 客户端发送到 /app/private.message
     * 服务端推送到 /user/{receiver}/queue/private-messages
     */
    @MessageMapping("/private.message")
    public void sendPrivateMessage(Map<String, String> payload, Principal principal,
                                   org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor) {
        String content = payload.get("content");
        String receiverUsername = payload.get("receiver");
        String senderUsername = principal.getName();
        String clientId = payload.get("clientId");

        // 发送频率限流（每用户每分钟 30 条）
        if (!allowSend(senderUsername)) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息发送过于频繁，请稍后再试")
            );
            return;
        }

        // 消息长度校验
        if (content == null || content.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息不能为空")
            );
            return;
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername, "/queue/send-error",
                    Map.of("type", "send_error", "message", "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 字")
            );
            return;
        }

        // 查询发送者（支持游客）
        User sender = userMapper.findByUsername(senderUsername);
        if (sender == null && senderUsername.startsWith("guest_")) {
            // 游客不能发私聊
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/send-error",
                    Map.of("type", "send_error", "message", "游客不能发送私聊消息")
            );
            return;
        }

        // 查询接收者
        User receiver = userMapper.findByUsername(receiverUsername);
        if (sender == null || receiver == null) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/send-error",
                    Map.of("type", "send_error", "message", "接收者不存在")
            );
            return;
        }

        // 私聊仅限好友关系（防止任意用户骚扰他人）
        if (!friendMapper.exists(sender.getId(), receiver.getId())) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/send-error",
                    Map.of("type", "send_error", "message", "只能向好友发送私聊消息")
            );
            return;
        }

        // 敏感词过滤
        FilterResult filterResult = sensitiveWordFilter.filter(content);
        if (filterResult.isBlocked()) {
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/send-error",
                    Map.of("type", "send_error", "subtype", "blocked", "message", filterResult.getMessage())
            );
            return;
        }
        if (filterResult.isReplaced()) {
            content = filterResult.getContent();
        }

        // 消息幂等：同一 clientId 只处理一次，防重连/双击重复发送（校验通过后占用）
        if (!acquireMessageIdempotent(senderUsername, clientId)) {
            return;
        }

        // 构造消息实体
        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setSenderName(sender.getName());
        message.setReceiverId(receiver.getId());
        message.setReceiverName(receiver.getUsername());
        message.setContent(content);
        message.setMessageType("text");
        message.setIsAiReply(0);
        message.setIsRecalled(0);
        message.setSenderIp(resolveClientIp(accessor));

        // 引用回复
        String replyToIdStr2 = payload.get("replyToId");
        if (replyToIdStr2 != null && !replyToIdStr2.isEmpty() && !"null".equals(replyToIdStr2)) {
            try {
                message.setReplyToId(Long.parseLong(replyToIdStr2));
            } catch (NumberFormatException ignored) {}
        }

        // 保存到数据库
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);

        // 构造响应对象
        MessageResponse response = toMessageResponse(message, sender);

        // 推送给接收者
        messagingTemplate.convertAndSendToUser(
                receiverUsername,
                "/queue/private-messages",
                response
        );

        // 也推送给发送者（让发送者也能看到自己发的消息）
        messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/private-messages",
                response
        );

        log.debug("私聊消息: {} -> {}", sender.getName(), receiver.getName());
    }

    /**
     * 心跳保活
     * 客户端发送到 /app/heartbeat
     */
    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        if (principal != null) {
            onlineUserService.updateHeartbeat(principal.getName());
        }
    }

    /**
     * 同步在线状态
     * 客户端发送到 /app/sync-state
     * 服务端广播到 /topic/onlineUsers
     */
    @MessageMapping("/sync-state")
    public void syncState() {
        broadcastOnlineUsers();
    }

    /**
     * 用户上线时调用（由拦截器触发）
     */
    public void onUserConnect(String username) {
        onlineUserService.userOnline(username);
        broadcastOnlineUsers();

        // 广播上线通知
        Map<String, String> notification = Map.of(
                "type", "user_online",
                "sender", username,
                "message", username + " 上线了",
                "sendTime", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/private-notifications", (Object) notification);
    }

    /**
     * 用户下线时调用
     */
    public void onUserDisconnect(String username) {
        onlineUserService.userOffline(username);
        broadcastOnlineUsers();

        // 广播下线通知
        Map<String, String> notification = Map.of(
                "type", "user_offline",
                "sender", username,
                "message", username + " 下线了",
                "sendTime", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/private-notifications", (Object) notification);
    }

    /**
     * 广播在线用户列表（返回 {username, name} 对象）
     */
    private void broadcastOnlineUsers() {
        Set<String> usernames = onlineUserService.getOnlineUsers();
        java.util.List<java.util.Map<String, String>> users = new java.util.ArrayList<>();

        if (usernames != null) {
            for (String username : usernames) {
                users.add(java.util.Map.of("username", username, "name", getDisplayName(username)));
            }
        }

        messagingTemplate.convertAndSend("/topic/onlineUsers",
                (Object) java.util.Map.of("onlineUsers", users, "count", users.size()));
    }

    /**
     * 根据用户名获取显示昵称（支持游客）
     */
    private String getDisplayName(String username) {
        // 游客从 Redis 获取
        if (username.startsWith("guest_")) {
            String guestKey = "chat:guest:" + username;
            java.util.Map<Object, Object> guestData = redisTemplate.opsForHash().entries(guestKey);
            String name = (String) guestData.get("name");
            return name != null ? name : username;
        }
        // 普通用户从数据库获取
        User user = userMapper.findByUsername(username);
        return user != null ? user.getName() : username;
    }

    /**
     * 从 STOMP 会话属性读取握手时采集的客户端 IP（无则返回 null）
     */
    private String resolveClientIp(org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor) {
        try {
            Object ip = accessor != null && accessor.getSessionAttributes() != null
                    ? accessor.getSessionAttributes().get("clientIp")
                    : null;
            return ip != null ? ip.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 消息幂等：同一 sender+clientId 只处理一次。
     * 用 Redis SETNX（NXXX) 保证原子占用，TTL 5 分钟（超过后允许重发）。
     * clientId 为空时视为不启用幂等（兼容旧客户端）。
     */
    private boolean acquireMessageIdempotent(String username, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return true;
        }
        String key = "chat:msg:idempotent:" + username + ":" + clientId;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1",
                java.time.Duration.ofMinutes(5));
        return Boolean.TRUE.equals(first);
    }

    /**
     * 实体 → 响应 DTO
     */
    private MessageResponse toMessageResponse(Message msg, User sender) {
        MessageResponse response = new MessageResponse();
        response.setId(msg.getId());
        response.setContent(msg.getContent());
        response.setMessageType(msg.getMessageType());
        response.setAiReply(msg.getIsAiReply() != null && msg.getIsAiReply() == 1);
        response.setRecalled(msg.getIsRecalled() != null && msg.getIsRecalled() == 1);
        response.setReplyToId(msg.getReplyToId());
        response.setCreatedAt(msg.getCreatedAt());

        // 发送者信息
        MessageResponse.SenderInfo senderInfo = new MessageResponse.SenderInfo();
        senderInfo.setId(sender.getId());
        senderInfo.setName(sender.getName());
        senderInfo.setAvatar(sender.getAvatar());
        response.setSender(senderInfo);

        // 接收者信息（私聊时）
        if (msg.getReceiverId() != null) {
            MessageResponse.ReceiverInfo receiverInfo = new MessageResponse.ReceiverInfo();
            receiverInfo.setId(msg.getReceiverId());
            receiverInfo.setName(msg.getReceiverName());
            response.setReceiver(receiverInfo);
        }

        return response;
    }
}
