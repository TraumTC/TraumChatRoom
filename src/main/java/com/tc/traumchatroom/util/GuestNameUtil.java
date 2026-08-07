package com.tc.traumchatroom.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * 游客名生成工具
 * 基于 User-Agent + IP 的 SHA-256 哈希生成唯一游客名
 * 格式：游客_7位哈希值
 */
public class GuestNameUtil {

    /**
     * 生成游客名
     * @param userAgent 浏览器 UA 字符串
     * @param ip 客户端 IP 地址
     * @return 游客名，如 "游客_a3b2c1d"
     */
    public static String generateGuestName(String userAgent, String ip) {
        // 拼接 UA + IP + 当前时间戳（秒），保证唯一性
        String raw = (userAgent != null ? userAgent : "") + "|" + ip + "|" + Instant.now().getEpochSecond();

        // SHA-256 哈希
        String hash = sha256(raw);

        // 取前 7 位作为游客标识
        String shortHash = hash.substring(0, 7);

        return "游客_" + shortHash;
    }

    /**
     * 生成游客用户名（登录用）
     * 格式：guest_时间戳_8位随机（时间戳+随机后缀保证高并发下不碰撞）
     */
    public static String generateGuestUsername() {
        String rand = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong());
        // 取后 8 位十六进制作为随机后缀，时间戳用毫秒
        String suffix = rand.length() > 8 ? rand.substring(rand.length() - 8) : rand;
        return "guest_" + System.currentTimeMillis() + "_" + suffix;
    }

    /**
     * SHA-256 哈希
     * 输入任意字符串，输出 64 位十六进制字符串
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 转为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是标准算法，不会出现这个异常
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}
