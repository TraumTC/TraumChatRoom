package com.tc.traumchatroom.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OnlineUserServiceImpl 单元测试（P1-8：按会话引用计数的在线状态）
 *
 * Lua 脚本本身的语义由真实 Redis 验证，这里覆盖 Java 侧契约：
 * key/ARGV 组装是否带上 sessionId，以及 Redis 故障时的降级方向。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlineUserServiceImplTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private OnlineUserServiceImpl service;

    @SuppressWarnings("unchecked")
    private void stubScriptResult(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private CapturedCall captureCall() {
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate)
                .execute(any(RedisScript.class), keys.capture(), args.capture());
        // 可变参数被整体捕获为一个数组
        return new CapturedCall(keys.getValue(), args.getValue());
    }

    private record CapturedCall(List<String> keys, Object[] argv) {
        /** 取第 index 个 ARGV（0-based，即 Lua 里的 ARGV[index+1]） */
        String arg(int index) {
            return String.valueOf(argv[index]);
        }
    }

    @Test
    void userOnlineTargetsPerUserSessionKeyAndAggregateKey() {
        stubScriptResult(1L);

        service.userOnline("alice", "sess-1");

        CapturedCall call = captureCall();
        assertThat(call.keys()).containsExactly("chat:online:sessions:alice", "chat:online:users");
        assertThat(call.arg(0)).isEqualTo("sess-1");          // ARGV[1] sessionId
        assertThat(call.arg(4)).isEqualTo("alice");           // ARGV[5] username
    }

    @Test
    void userOnlineReturnsTrueOnlyForFirstSession() {
        stubScriptResult(1L);
        assertThat(service.userOnline("alice", "s1")).isTrue();

        stubScriptResult(0L);
        assertThat(service.userOnline("alice", "s2")).isFalse();
    }

    @Test
    void userOfflineReturnsTrueOnlyForLastSession() {
        stubScriptResult(0L);
        assertThat(service.userOffline("alice", "s1")).isFalse();

        stubScriptResult(1L);
        assertThat(service.userOffline("alice", "s2")).isTrue();
    }

    @Test
    void staleSessionCutoffIsHeartbeatTimeoutBehindNow() {
        stubScriptResult(1L);

        long before = System.currentTimeMillis();
        service.userOnline("alice", "s1");
        long after = System.currentTimeMillis();

        CapturedCall call = captureCall();
        long now = Long.parseLong(call.arg(1));      // ARGV[2] now
        long cutoff = Long.parseLong(call.arg(2));   // ARGV[3] 僵尸会话 cutoff
        assertThat(now).isBetween(before, after);
        // 5 分钟心跳超时
        assertThat(now - cutoff).isEqualTo(5 * 60 * 1000L);
    }

    // ---------- Redis 故障时的降级方向 ----------

    @Test
    void userOnlineFailureIsTreatedAsNotFirstSession() {
        stubScriptFailure();
        // 宁可少弹一次「上线了」，也不误报
        assertThat(service.userOnline("alice", "s1")).isFalse();
    }

    @Test
    void userOfflineFailureIsTreatedAsNotLastSession() {
        stubScriptFailure();
        // 关键：不能因为 Redis 抖动就把整个账号判离线
        assertThat(service.userOffline("alice", "s1")).isFalse();
    }

    @Test
    void nullScriptResultIsTreatedAsNoTransition() {
        stubScriptResult(null);
        assertThat(service.userOnline("alice", "s1")).isFalse();
        assertThat(service.userOffline("alice", "s1")).isFalse();
    }

    @Test
    void heartbeatFailureIsSwallowed() {
        stubScriptFailure();
        // 心跳失败不应向上抛，否则一次 Redis 抖动会打断 WS 入站处理
        service.updateHeartbeat("alice", "s1");
    }

    @SuppressWarnings("unchecked")
    private void stubScriptFailure() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("Redis 连接超时"));
    }
}
