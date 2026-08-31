package com.tc.traumchatroom.service;

import com.tc.traumchatroom.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Refresh Token Redis 存储：按用户会话隔离并只保存 SHA-256。
 */
@Slf4j
@Service
public class RefreshTokenStore {

    private static final String PREFIX = "chat:refresh:";
    private static final String SESSIONS_PREFIX = "chat:refresh-sessions:";

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]) " +
                            "if KEYS[1] ~= KEYS[2] then redis.call('DEL', KEYS[1]) end " +
                            "redis.call('SADD', KEYS[3], ARGV[4]) " +
                            "redis.call('PEXPIRE', KEYS[3], ARGV[6]) " +
                            "if ARGV[5] ~= '' and ARGV[5] ~= ARGV[4] then redis.call('SREM', KEYS[3], ARGV[5]) end " +
                            "return 1 else return 0 end", Long.class);

    private static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) " +
                            "redis.call('SADD', KEYS[2], ARGV[3]) " +
                            "redis.call('PEXPIRE', KEYS[2], ARGV[4]) return 1", Long.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "redis.call('DEL', KEYS[1]) redis.call('SREM', KEYS[2], ARGV[2]) return 1 " +
                            "else return 0 end",
                    Long.class);

    private static final DefaultRedisScript<Long> REVOKE_ALL_SCRIPT =
            new DefaultRedisScript<>(
                    "local ids = redis.call('SMEMBERS', KEYS[1]) " +
                            "for _, id in ipairs(ids) do redis.call('DEL', ARGV[1] .. id) end " +
                            "return redis.call('DEL', KEYS[1])", Long.class);

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    public void save(String username, String token, Duration ttl) {
        String sessionId = requireSessionId(token);
        Long result;
        try {
            result = redisTemplate.execute(SAVE_SCRIPT,
                    java.util.List.of(sessionKey(username, sessionId), sessionsKey(username)),
                    hash(token), String.valueOf(ttl.toMillis()), sessionId,
                    String.valueOf(indexTtlMillis(ttl)));
        } catch (Exception e) {
            log.error("保存 refreshToken 会话失败: username={}, sessionId={}", username, sessionId, e);
            throw unavailable(e);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw unavailable(null);
        }
    }

    /** 原子校验旧令牌并轮换为新令牌。 */
    public boolean rotate(String username, String oldToken, String newToken, Duration ttl) {
        String oldSessionId = sessionId(oldToken);
        String newSessionId = sessionId(newToken);
        if (newSessionId == null) return false;

        if (oldSessionId == null) return false;
        String sourceKey = sessionKey(username, oldSessionId);
        String expected = hash(oldToken);
        String targetKey = sessionKey(username, newSessionId);
        Long result;
        try {
            result = redisTemplate.execute(
                    ROTATE_SCRIPT,
                    java.util.List.of(sourceKey, targetKey, sessionsKey(username)),
                    expected, hash(newToken), String.valueOf(ttl.toMillis()), newSessionId, oldSessionId,
                    String.valueOf(indexTtlMillis(ttl)));
        } catch (Exception e) {
            log.error("轮换 refreshToken 会话失败: username={}, sessionId={}", username, oldSessionId, e);
            throw unavailable(e);
        }
        return Long.valueOf(1L).equals(result);
    }

    /** 仅当当前 Redis 记录仍对应此令牌时撤销，避免旧令牌删除新会话。 */
    public boolean revoke(String username, String token) {
        String sessionId = sessionId(token);
        if (sessionId == null) return false;
        String key = sessionKey(username, sessionId);
        String expected = hash(token);
        Long result;
        try {
            result = redisTemplate.execute(REVOKE_SCRIPT,
                    java.util.List.of(key, sessionsKey(username)), expected, sessionId);
        } catch (Exception e) {
            log.error("撤销 refreshToken 会话失败: username={}, sessionId={}", username, sessionId, e);
            throw unavailable(e);
        }
        return Long.valueOf(1L).equals(result);
    }

    /** accessToken 所属会话是否仍有效；登出、改密、禁用后立即返回 false。 */
    public boolean isSessionActive(String username, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(username, sessionId)));
        } catch (Exception e) {
            log.error("Redis 会话校验不可用，拒绝访问: username={}", username, e);
            throw unavailable(e);
        }
    }

    /** 撤销用户全部会话，用于改密、禁用账号和“退出所有设备”。 */
    public void revokeAll(String username) {
        try {
            redisTemplate.execute(REVOKE_ALL_SCRIPT,
                    java.util.List.of(sessionsKey(username)),
                    PREFIX + "{" + username + "}:");
        } catch (Exception e) {
            // 改密/禁用场景必须显式失败，不能误以为会话已经撤销。
            log.error("撤销用户全部会话失败: username={}", username, e);
            throw unavailable(e);
        }
    }

    private String sessionId(String token) {
        try {
            return jwtUtil.getSessionIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    private String sessionKey(String username, String sessionId) {
        return PREFIX + "{" + username + "}:" + sessionId;
    }

    private String sessionsKey(String username) {
        return SESSIONS_PREFIX + "{" + username + "}";
    }

    private long indexTtlMillis(Duration tokenTtl) {
        return Math.max(tokenTtl.toMillis(), jwtUtil.getRefreshExpirationMillis());
    }

    private String requireSessionId(String token) {
        String id = sessionId(token);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("refreshToken 缺少会话 ID");
        }
        return id;
    }

    private BusinessException unavailable(Throwable cause) {
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "认证会话服务暂时不可用", cause);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
