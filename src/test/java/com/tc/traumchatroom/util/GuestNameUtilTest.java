package com.tc.traumchatroom.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GuestNameUtil 单元测试
 */
class GuestNameUtilTest {

    @Test
    void generateGuestNameFormat() {
        String name = GuestNameUtil.generateGuestName("Mozilla/5.0", "192.168.1.100");
        // 格式：游客_7位哈希
        assertThat(name).startsWith("游客_");
        assertThat(name).hasSize("游客_".length() + 7);
    }

    @Test
    void generateGuestNameAlwaysValidFormat() {
        // 时间戳为秒级，连续两次调用可能落在同一秒 → 不断言不同，只断言格式合法
        for (int i = 0; i < 5; i++) {
            String name = GuestNameUtil.generateGuestName("Mozilla/5.0", "192.168.1.100");
            assertThat(name).startsWith("游客_");
            assertThat(name).hasSize("游客_".length() + 7);
        }
    }

    @Test
    void generateGuestUsernameFormat() {
        String username = GuestNameUtil.generateGuestUsername();
        assertThat(username).startsWith("guest_");
    }

    @Test
    void generateGuestUsernameUniqueUnderConcurrency() {
        // 快速连续生成，验证时间戳 + 随机后缀保证不碰撞
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(GuestNameUtil.generateGuestUsername());
        }
        assertThat(seen).hasSize(1000);
    }
}
