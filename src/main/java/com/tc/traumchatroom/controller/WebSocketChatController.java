package com.tc.traumchatroom.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.traumchatroom.config.AiConfig;
import com.tc.traumchatroom.config.ChatRateLimitConfig;
import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.AiService;
import com.tc.traumchatroom.service.FilterResult;
import com.tc.traumchatroom.service.OnlineUserService;
import com.tc.traumchatroom.service.SensitiveWordFilter;
import com.tc.traumchatroom.util.RedisRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private AiConfig aiConfig;

    @Resource
    private ChatRateLimitConfig chatRateLimitConfig;

    @Resource
    private RedisRateLimiter redisRateLimiter;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private com.tc.traumchatroom.mapper.FriendMapper friendMapper;

    /** AI 回复异步线程池（见 AsyncConfig.aiTaskExecutor） */
    @Resource(name = "aiTaskExecutor")
    private java.util.concurrent.Executor aiTaskExecutor;

    /** 消息内容最大长度（防超长消息 DoS） */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /**
     * 群聊 @提及未读 Redis Key 前缀：chat:mention:v2:{username}
     * ZSet 结构：member = 提醒 JSON，score = messageId
     * （读取与剔除侧见 ChatServiceImpl#MENTION_KEY_PREFIX，两处需保持一致）
     */
    private static final String MENTION_KEY_PREFIX = "chat:mention:v2:";
    /** @提及未读 TTL：7 天 */
    private static final Duration MENTION_TTL = Duration.ofDays(7);
    /** 单个用户 @提及未读上限 */
    private static final long MENTION_MAX = 50;
    /**
     * @提及未读入库：ZADD 以 messageId 为 score，再按 rank 截断到最新 MENTION_MAX 条。
     * rank 按 score 升序，因此超量时删的是 score 最小（最旧）的那几条。
     */
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> MENTION_PUSH_SCRIPT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "redis.call('ZADD', KEYS[1], tonumber(ARGV[2]), ARGV[1]) " +
                            "local excess = redis.call('ZCARD', KEYS[1]) - tonumber(ARGV[3]) " +
                            "if excess > 0 then " +
                            "  redis.call('ZREMRANGEBYRANK', KEYS[1], 0, excess - 1) " +
                            "end " +
                            "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4])) return 1", Long.class);
    /** @提及提醒消息摘要最大长度 */
    private static final int MENTION_CONTENT_MAX = 120;
    /**
     * @提及正则：昵称通常由字母、数字、下划线、连字符或汉字组成。
     * 不把逗号、句号等标点吞进昵称，避免「@张三，」无法匹配真实用户。
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_-]+)");
    /** AI 助手昵称（触发 AI 链路，不参与 @提醒） */
    private static final String AI_NICKNAME = "小汤";

    /**
     * 消息发送限流（Redis 原子计数，每用户每分钟最多 sendMaxPerMinute 条，读配置 chat.rate-limit.send-max-per-minute）
     * @return true 允许发送；false 超限
     */
    private boolean allowSend(String username) {
        String key = "chat:send:rate:" + username;
        if (!redisRateLimiter.tryAcquire(key, chatRateLimitConfig.getSendMaxPerMinute(), 60)) {
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
        if (principal == null) return;
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

        // 游客拦截：游客使用 @AI（@小汤）→ 消息不发送，仅提示（游客不能使用 AI 助手）
        if ("ROLE_GUEST".equals(sender.getRole()) && aiService.detectAiMention(content)) {
            sendError(username, clientId, "游客暂不能使用 AI 助手，登录后即可体验", null);
            return;
        }

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

        // @提及提醒：解析被@用户，在线实时推送 + 离线未读累计（不走 AI，快路径无 @ 直接跳过）
        sendMentionNotices(content, message, sender);

        log.debug("群聊消息: {} -> {}", sender.getName(), content);

        // 检测 @小汤，触发 AI 回复（游客已被上方拦截，此处仅处理登录用户）
        if (aiService.detectAiMention(content)) {
            triggerAiReply(content, message.getId(), sender.getUsername());
        }
    }

    /**
     * 群聊 @提及提醒：
     * 1. 解析消息中的 @昵称（排除 AI 助手与发送者自己）
     * 2. 对被 @用户实时推送 /user/queue/mention-notice
     * 3. 离线未读累计到 Redis List（TTL 7 天，上限 50 条），前端上线可拉取
     * Redis 异常不影响消息主链路（try-catch 降级）
     */
    private void sendMentionNotices(String content, Message message, User sender) {
        if (content == null || !content.contains("@")) return;

        Set<String> names = extractMentions(content);
        names.remove(AI_NICKNAME);
        if (names.isEmpty()) return;

        try {
            List<User> targets = userMapper.findByNames(new ArrayList<>(names));
            if (targets.isEmpty()) return;

            String summary = content.length() > MENTION_CONTENT_MAX
                    ? content.substring(0, MENTION_CONTENT_MAX) : content;
            // 扁平结构，与 MentionNoticeVO 字段一致（离线未读直接反序列化为该 VO）
            Map<String, Object> payload = Map.of(
                    "type", "mention",
                    "senderUsername", sender.getUsername(),
                    "senderName", sender.getName(),
                    "messageId", message.getId(),
                    "content", summary,
                    "createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : ""
            );

            for (User target : targets) {
                // 仅排除发送者本人；按昵称排除会误伤昵称相同的其他用户。
                if (target.getUsername().equals(sender.getUsername())) continue;
                // 在线用户实时推送（传对象，由 STOMP converter 序列化，与全项目其他推送一致；
                // 离线用户 STOMP 静默丢弃）
                messagingTemplate.convertAndSendToUser(target.getUsername(), "/queue/mention-notice", payload);
                // 离线未读累计（Redis 故障时降级，不影响消息发送）
                try {
                    String key = MENTION_KEY_PREFIX + target.getUsername();
                    redisTemplate.execute(MENTION_PUSH_SCRIPT, java.util.List.of(key),
                            objectMapper.writeValueAsString(payload),
                            String.valueOf(message.getId()),
                            String.valueOf(MENTION_MAX), String.valueOf(MENTION_TTL.getSeconds()));
                } catch (Exception e) {
                    log.warn("@提及未读累计失败: target={}", target.getUsername(), e);
                }
            }
        } catch (Exception e) {
            log.warn("@提及提醒处理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析消息中的 @提及昵称（去重、保序）
     */
    public static Set<String> extractMentions(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null) return names;
        Matcher m = MENTION_PATTERN.matcher(content);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
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
                    aiUser.setName("小汤");
                    aiUser.setPassword("ai-no-password");
                    aiUser.setRole("ROLE_AI");
                    aiUser.setStatus(1);
                    aiUser.setAvatar(aiConfig.getAvatarUrl());
                    userMapper.insertIgnore(aiUser);
                    aiUser = userMapper.findByUsername("ai_xiaoai");
                    if (aiUser == null) {
                        log.error("AI 用户创建失败，跳过 AI 回复");
                        return;
                    }
                }
                // 已存在的 AI 用户若头像为空，用配置的头像兜底（聊天消息 sender 头像即时生效）
                if (aiUser.getAvatar() == null || aiUser.getAvatar().isBlank()) {
                    aiUser.setAvatar(aiConfig.getAvatarUrl());
                }

                Message aiMessage = new Message();
                aiMessage.setSenderId(aiUser.getId());
                aiMessage.setSenderName("小汤");
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
        if (principal == null) return;
        String content = payload.get("content");
        String receiverUsername = payload.get("receiver");
        String senderUsername = principal.getName();
        String clientId = payload.get("clientId");

        log.info("私聊消息请求: sender={}, receiver={}, clientId={}, content长度={}",
                senderUsername, receiverUsername, clientId, content != null ? content.length() : 0);

        // 发送频率限流（每用户每分钟 30 条）
        if (!allowSend(senderUsername)) {
            log.warn("私聊被限流: sender={}", senderUsername);
            sendError(senderUsername, clientId, "消息发送过于频繁，请稍后再试", null);
            return;
        }

        // 消息长度校验
        if (content == null || content.isBlank()) {
            log.warn("私聊内容为空: sender={}", senderUsername);
            sendError(senderUsername, clientId, "消息不能为空", null);
            return;
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            log.warn("私聊内容超长: sender={}, length={}", senderUsername, content.length());
            sendError(senderUsername, clientId, "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 字", null);
            return;
        }

        // 查询发送者（支持游客）
        User sender = userMapper.findByUsername(senderUsername);
        if (sender == null && senderUsername.startsWith("guest_")) {
            log.warn("私聊被拒-游客: sender={}", senderUsername);
            sendError(senderUsername, clientId, "游客不能发送私聊消息", null);
            return;
        }

        // 查询接收者
        User receiver = userMapper.findByUsername(receiverUsername);
        if (sender == null || receiver == null) {
            log.warn("私聊被拒-用户不存在: sender={}, receiver={}, senderFound={}, receiverFound={}",
                    senderUsername, receiverUsername, sender != null, receiver != null);
            sendError(senderUsername, clientId, "接收者不存在", null);
            return;
        }

        // 私聊权限：登录用户间任意可发，非好友仅在线时可发
        // 游客已在上方拦截，此处 sender 必为登录用户
        boolean receiverOnline = onlineUserService.isOnline(receiverUsername);
        if (!receiverOnline && !friendMapper.exists(sender.getId(), receiver.getId())) {
            // 接收者离线且非好友 → 拒绝（无法离线投递）
            log.warn("私聊被拒-对方离线且非好友: sender={}, receiver={}", senderUsername, receiverUsername);
            sendError(senderUsername, clientId, "对方不在线，添加好友后才能发送离线消息", null);
            return;
        }
        // 在线或好友 → 放行

        // 敏感词过滤
        FilterResult filterResult = sensitiveWordFilter.filter(content);
        if (filterResult.isBlocked()) {
            sendError(senderUsername, clientId, filterResult.getMessage(), "blocked");
            return;
        }
        if (filterResult.isReplaced()) {
            content = filterResult.getContent();
        }

        // 消息幂等：同一 clientId 只处理一次，防重连/双击重复发送（校验通过后占用）
        if (!acquireMessageIdempotent(senderUsername, clientId)) {
            log.warn("私聊被拒-幂等重复: sender={}, clientId={}", senderUsername, clientId);
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
        try {
            messageMapper.insert(message);
            log.info("私聊消息已保存: id={}, sender={}, receiver={}", message.getId(), senderUsername, receiverUsername);
        } catch (Exception e) {
            log.error("私聊消息保存失败: sender={}, receiver={}", senderUsername, receiverUsername, e);
            sendError(senderUsername, clientId, "消息保存失败", null);
            return;
        }

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
    public void heartbeat(Principal principal,
                          org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor) {
        if (principal != null && accessor != null) {
            onlineUserService.updateHeartbeat(principal.getName(), accessor.getSessionId());
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
     *
     * 同一账号可多设备并行在线，因此上线通知与在线列表广播都只在状态真正跃迁
     * （首个会话建立）时发送：后续设备接入时在线集合没有变化，广播出去的内容与上一次
     * 完全相同，纯属浪费 —— 100 人在线时一次网络抖动引发的重连会触发一次全员广播。
     * 新接入的客户端自己会在 onConnect 里调 /app/sync-state 取一次列表，不依赖这里。
     */
    public void onUserConnect(String username, String sessionId) {
        boolean firstSession = onlineUserService.userOnline(username, sessionId);
        if (!firstSession) {
            // 该用户已有其它设备在线，在线集合未变，不广播
            return;
        }

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
     *
     * 只有最后一个会话断开才算真正离线；关掉多设备中的任意一台既不广播「下线了」，
     * 也不重发在线列表（集合没变）。
     */
    public void onUserDisconnect(String username, String sessionId) {
        boolean lastSession = onlineUserService.userOffline(username, sessionId);
        if (!lastSession) {
            // 还有其它设备在线，在线集合未变，不广播
            return;
        }

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
     *
     * 显示名批量解析：原实现对每个在线用户调一次 getDisplayName（查库或查 Redis），
     * 100 人在线时一次广播就是 100 次往返，而广播本身在每次上下线时都会触发。
     * 现在拆成「注册用户一次 IN 查询 + 游客一次 Redis pipeline」，固定 2 次往返。
     */
    private void broadcastOnlineUsers() {
        Set<String> usernames = onlineUserService.getOnlineUsers();
        java.util.List<java.util.Map<String, String>> users = new java.util.ArrayList<>();

        if (usernames != null && !usernames.isEmpty()) {
            java.util.Map<String, String> displayNames = resolveDisplayNames(usernames);
            for (String username : usernames) {
                users.add(java.util.Map.of("username", username,
                        "name", displayNames.getOrDefault(username, username)));
            }
        }

        messagingTemplate.convertAndSend("/topic/onlineUsers",
                (Object) java.util.Map.of("onlineUsers", users, "count", users.size()));
    }

    /**
     * 批量解析显示名：注册用户走一次 IN 查询，游客走一次 Redis pipeline。
     * 解析不到的回退为 username 本身，与原 getDisplayName 的兜底行为一致。
     */
    private java.util.Map<String, String> resolveDisplayNames(Set<String> usernames) {
        java.util.Map<String, String> result = new java.util.HashMap<>();

        java.util.List<String> guests = new java.util.ArrayList<>();
        java.util.List<String> registered = new java.util.ArrayList<>();
        for (String u : usernames) {
            if (u == null) continue;
            if (u.startsWith("guest_")) guests.add(u); else registered.add(u);
        }

        // 注册用户：一次 IN 查询
        if (!registered.isEmpty()) {
            try {
                for (User u : userMapper.findByUsernames(registered)) {
                    if (u.getName() != null) result.put(u.getUsername(), u.getName());
                }
            } catch (Exception e) {
                log.warn("批量解析在线用户昵称失败，回退为用户名", e);
            }
        }

        // 游客：一次 pipeline 取回全部 guest hash 的 name 字段
        if (!guests.isEmpty()) {
            try {
                java.util.List<Object> names = redisTemplate.executePipelined(
                        (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                            for (String g : guests) {
                                connection.hashCommands().hGet(
                                        ("chat:guest:" + g).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                        "name".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            return null;
                        });
                for (int i = 0; i < guests.size() && i < names.size(); i++) {
                    Object name = names.get(i);
                    if (name != null) result.put(guests.get(i), name.toString());
                }
            } catch (Exception e) {
                log.warn("批量解析游客昵称失败，回退为用户名", e);
            }
        }

        return result;
    }

    /**
     * 向发送者推送发送错误（/queue/send-error）
     * clientId 可选：携带后前端可精确移除本地乐观更新的临时消息（防空闲残留）
     */
    private void sendError(String username, String clientId, String message, String subtype) {
        java.util.HashMap<String, String> payload = new java.util.HashMap<>();
        payload.put("type", "send_error");
        payload.put("message", message);
        if (subtype != null) payload.put("subtype", subtype);
        if (clientId != null) payload.put("clientId", clientId);
        messagingTemplate.convertAndSendToUser(username, "/queue/send-error", payload);
    }

    @MessageExceptionHandler(com.tc.traumchatroom.exception.BusinessException.class)
    public void handleWebSocketBusinessException(com.tc.traumchatroom.exception.BusinessException e,
                                                  Principal principal) {
        String username = principal != null ? principal.getName() : null;
        log.warn("WebSocket 业务异常: username={}, code={}, message={}",
                username, e.getErrorCode().getCode(), e.getMessage());
        if (username != null) sendError(username, null, e.getMessage(), "business");
    }

    @MessageExceptionHandler(Exception.class)
    public void handleWebSocketException(Exception e, Principal principal) {
        String username = principal != null ? principal.getName() : null;
        log.error("WebSocket 未知异常: username={}, exception={}",
                username, e.getClass().getName(), e);
        if (username != null) sendError(username, null, "消息处理失败，请稍后再试", "system");
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
        senderInfo.setUsername(sender.getUsername());
        senderInfo.setName(sender.getName());
        senderInfo.setAvatar(sender.getAvatar());
        response.setSender(senderInfo);

        // 接收者信息（私聊时，receiver_name 语义为 username）
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
