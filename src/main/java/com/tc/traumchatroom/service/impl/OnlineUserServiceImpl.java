package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.service.OnlineUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.List;

/**
 * 在线用户服务实现
 *
 * 两级结构：
 * <pre>
 * chat:online:sessions:{username}   ZSet: member = STOMP sessionId, score = 该会话最后心跳时间
 * chat:online:users                 ZSet: member = username,        score = 最后心跳时间
 * </pre>
 *
 * 会话键承担引用计数：同一账号多设备并行登录时每个连接一个成员，
 * 只有最后一个会话断开才把用户从聚合键移除。
 * 聚合键结构保持不变，因此 isOnline / getOnlineUsers 仍是 O(1)/O(logN) ——
 * 这两个方法在热路径上（每条私聊一次、好友列表每个好友一次），不能退化成扫描。
 *
 * 僵尸会话（进程被杀、DISCONNECT 事件丢失）不会把用户永久钉在「在线」：
 * 注册与注销脚本都会先按心跳超时清掉过期成员，再判断计数，因此引用计数可自愈。
 */
@Slf4j
@Service
public class OnlineUserServiceImpl implements OnlineUserService {

    private static final String ONLINE_USERS_KEY = "chat:online:users";
    /** 用户会话集合 Key 前缀：chat:online:sessions:{username} */
    private static final String SESSION_KEY_PREFIX = "chat:online:sessions:";
    /** 心跳超时：5 分钟（300000 毫秒）无心跳视为离线（前端 20s 一次心跳，容忍 15 次丢失） */
    private static final long HEARTBEAT_TIMEOUT_MS = 5 * 60 * 1000;
    /** 在线 ZSet 整体 TTL：1 小时，活跃时刷新；长期无活动自动消失，避免 key 常驻 */
    private static final Duration ONLINE_KEY_TTL = Duration.ofMinutes(60);

    /**
     * 注册会话。
     * KEYS[1]=会话键 KEYS[2]=聚合键；ARGV[1]=sessionId ARGV[2]=now ARGV[3]=cutoff ARGV[4]=ttl ARGV[5]=username
     * @return 1 表示注册前该用户无存活会话（离线→在线跃迁）
     */
    private static final DefaultRedisScript<Long> SESSION_REGISTER_SCRIPT = new DefaultRedisScript<>(
            // 先清掉心跳超时的僵尸会话，保证 ZCARD 反映的是真实存活数
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3]) " +
                    "local first = 0 " +
                    "if redis.call('ZCARD', KEYS[1]) == 0 then first = 1 end " +
                    "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]) " +
                    "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[5]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[4]) " +
                    "return first", Long.class);

    /**
     * 注销会话。
     * KEYS/ARGV 同上。
     * @return 1 表示这是最后一个会话，用户已从聚合键移除（在线→离线跃迁）
     */
    private static final DefaultRedisScript<Long> SESSION_UNREGISTER_SCRIPT = new DefaultRedisScript<>(
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
                    // 顺手清理僵尸会话，避免它们把用户永久钉在「在线」
                    "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3]) " +
                    "if redis.call('ZCARD', KEYS[1]) == 0 then " +
                    "  redis.call('DEL', KEYS[1]) " +
                    "  redis.call('ZREM', KEYS[2], ARGV[5]) " +
                    "  return 1 " +
                    "end " +
                    // 还有别的设备在线：刷新聚合 score，保持用户在线
                    "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[5]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[4]) " +
                    "return 0", Long.class);

    /**
     * 心跳：同时刷新会话与聚合的 score。
     * KEYS/ARGV 同上（不使用 ARGV[3]）。
     */
    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>(
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]) " +
                    "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[5]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                    "redis.call('EXPIRE', KEYS[2], ARGV[4]) return 1", Long.class);

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean userOnline(String username, String sessionId) {
        try {
            Long first = runSessionScript(SESSION_REGISTER_SCRIPT, username, sessionId);
            log.debug("用户上线: {} (session={}, 首个会话={})", username, sessionId, first);
            return first != null && first == 1L;
        } catch (Exception e) {
            // 降级取保守方向：当作非首次上线，宁可少弹一次「上线了」也不误报
            log.warn("记录用户上线状态失败: user={}, session={}", username, sessionId, e);
            return false;
        }
    }

    @Override
    public boolean userOffline(String username, String sessionId) {
        try {
            Long last = runSessionScript(SESSION_UNREGISTER_SCRIPT, username, sessionId);
            log.debug("用户下线: {} (session={}, 最后会话={})", username, sessionId, last);
            return last != null && last == 1L;
        } catch (Exception e) {
            // 降级取保守方向：当作还有其它设备在线，避免误判整个账号离线
            log.warn("记录用户下线状态失败: user={}, session={}", username, sessionId, e);
            return false;
        }
    }

    @Override
    public Set<String> getOnlineUsers() {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TIMEOUT_MS;
        // 获取最近 5 分钟内有心跳的用户（过期成员由定时任务清理，此处只读）
        try {
            Set<String> users = redisTemplate.opsForZSet().rangeByScore(ONLINE_USERS_KEY, cutoff, Double.MAX_VALUE);
            return users != null ? users : Collections.emptySet();
        } catch (Exception e) {
            log.warn("读取在线用户失败，返回空列表", e);
            return Collections.emptySet();
        }
    }

    @Override
    public boolean isOnline(String username) {
        Double score;
        try {
            score = redisTemplate.opsForZSet().score(ONLINE_USERS_KEY, username);
        } catch (Exception e) {
            log.warn("读取在线状态失败: {}", username, e);
            return false;
        }
        if (score == null) return false;
        return (System.currentTimeMillis() - score) < HEARTBEAT_TIMEOUT_MS;
    }

    @Override
    public int getOnlineCount() {
        return getOnlineUsers().size();
    }

    @Override
    public void updateHeartbeat(String username, String sessionId) {
        try {
            runSessionScript(HEARTBEAT_SCRIPT, username, sessionId);
        } catch (Exception e) {
            log.warn("更新用户心跳失败: user={}, session={}", username, sessionId, e);
        }
    }

    /**
     * 定时清理过期成员（每分钟），不依赖广播时机，保证在线列表实时准确。
     * 兜底「某用户所有会话都静默死亡」的情况——此时没有任何 DISCONNECT 事件可触发注销，
     * 只能靠聚合键的心跳 score 过期来下线。会话键自身由 TTL 回收。
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TIMEOUT_MS;
        try {
            redisTemplate.opsForZSet().removeRangeByScore(ONLINE_USERS_KEY, Double.NEGATIVE_INFINITY, cutoff);
        } catch (Exception e) {
            log.warn("清理在线用户失败", e);
        }
    }

    /**
     * 三个会话脚本共用同一套 KEYS/ARGV 约定，这里统一组装
     */
    private Long runSessionScript(DefaultRedisScript<Long> script, String username, String sessionId) {
        long now = System.currentTimeMillis();
        return redisTemplate.execute(script,
                List.of(SESSION_KEY_PREFIX + username, ONLINE_USERS_KEY),
                sessionId,                                          // ARGV[1]
                String.valueOf(now),                                // ARGV[2]
                String.valueOf(now - HEARTBEAT_TIMEOUT_MS),         // ARGV[3] 僵尸会话 cutoff
                String.valueOf(ONLINE_KEY_TTL.getSeconds()),        // ARGV[4]
                username);                                          // ARGV[5]
    }
}
