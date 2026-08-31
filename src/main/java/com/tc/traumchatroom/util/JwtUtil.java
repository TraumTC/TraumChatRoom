package com.tc.traumchatroom.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类 — 生成、解析、验证 Token
 *
 * JWT 由三部分组成：Header.Payload.Signature
 * - Header：算法和类型
 * - Payload：用户信息（用户名、过期时间）
 * - Signature：签名（用密钥对 Header+Payload 签名，防篡改）
 */
@Component
public class JwtUtil {

    /** HMAC-SHA256 要求密钥至少 32 字节（256 位） */
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 启动校验：JWT 密钥缺失或过短时拒绝启动。
     * 防止密钥未配置（硬编码默认值已移除）或配置了弱密钥导致可被伪造。
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置。请通过环境变量 JWT_SECRET 注入密钥（至少 " + MIN_SECRET_BYTES + " 字节）。");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，无法安全签名。请更换更强的密钥。");
        }
    }

    /**
     * 获取签名密钥
     * 密钥至少 256 位（32 字节），用于 HMAC-SHA256 签名
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌（accessToken）
     * @param username 用户名（存入 Token 的标识）
     * @return JWT Token 字符串
     */
    public String generateAccessToken(String username) {
        return generateAccessToken(username, UUID.randomUUID().toString());
    }

    public String generateAccessToken(String username, String sessionId) {
        return Jwts.builder()
                .subject(username)                          // 设置主题（用户名）
                .claim("token_type", "access")
                .claim("sid", sessionId)
                .issuedAt(new Date())                       // 签发时间
                .expiration(new Date(System.currentTimeMillis() + accessExpiration)) // 过期时间
                .signWith(getSigningKey())                  // 签名
                .compact();                                 // 生成字符串
    }

    /**
     * 生成刷新令牌（refreshToken）
     * 有效期比 accessToken 长，用于刷新
     */
    public String generateRefreshToken(String username) {
        return generateRefreshToken(username, UUID.randomUUID().toString(), refreshExpiration);
    }

    /** 生成带会话 ID 的刷新令牌，支持同一账号多设备并行登录。 */
    public String generateRefreshToken(String username, String sessionId) {
        return generateRefreshToken(username, sessionId, refreshExpiration);
    }

    /** 生成指定有效期的刷新令牌（游客会话使用较短 TTL）。 */
    public String generateRefreshToken(String username, String sessionId, long expiration) {
        return Jwts.builder()
                .subject(username)
                .claim("token_type", "refresh")
                .claim("sid", sessionId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** 从刷新令牌中获取会话 ID；旧版本令牌没有 sid 时返回 null。 */
    public String getSessionIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("sid", String.class);
    }

    public long getRefreshExpirationMillis() {
        return refreshExpiration;
    }

    public long getRemainingValidityMillis(String token) {
        Claims claims = parseToken(token);
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }

    /**
     * 从 Token 中解析用户名
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 验证 Token 是否有效
     * @param token JWT Token
     * @return true=有效，false=无效或过期
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date()); // 未过期则有效
        } catch (Exception e) {
            return false; // 解析失败说明 Token 无效
        }
    }

    /** 仅验证 accessToken，防止 refreshToken 被直接当作接口凭证使用。 */
    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("token_type", String.class))
                    && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /** 仅验证 refreshToken。 */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("token_type", String.class))
                    && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 解析 Token，返回 Claims（Payload 内容）
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())    // 用密钥验证签名
                .build()
                .parseSignedClaims(token)       // 解析 Token
                .getPayload();                  // 获取 Payload
    }
}
