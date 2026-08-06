package com.tc.traumchatroom.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

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
        return Jwts.builder()
                .subject(username)                          // 设置主题（用户名）
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
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
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
