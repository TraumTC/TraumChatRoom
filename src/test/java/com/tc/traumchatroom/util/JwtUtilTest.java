package com.tc.traumchatroom.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 单元测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 注入配置（secret 至少 32 字节以满足 HMAC-SHA256）
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-256bit-0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 24 * 60 * 60 * 1000L); // 24h
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L); // 7d
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = jwtUtil.generateAccessToken("zhangsan");
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("zhangsan");
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void generateRefreshTokenIsValid() {
        String token = jwtUtil.generateRefreshToken("lisi");
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("lisi");
    }

    @Test
    void expiredTokenIsInvalid() {
        // 把 accessExpiration 设为负数，生成的 token 立即过期
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", -1000L);
        String token = jwtUtil.generateAccessToken("zhangsan");
        assertThat(jwtUtil.validateToken(token)).isFalse();
        assertThat(jwtUtil.isTokenExpired(token)).isTrue();
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtUtil.generateAccessToken("zhangsan");
        // 篡改 token 最后一个字符，破坏签名
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void garbageTokenIsInvalid() {
        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse();
    }
}
