package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.util.IpUtil;
import com.tc.traumchatroom.util.RedisRateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流切面的主体解析测试。
 *
 * 重点覆盖 by = IP 这条路径：匿名接口按 USER 限流会退化成全站共用一个配额
 * （Authentication 是 AnonymousAuthenticationToken，getName() 恒为 "anonymousUser"），
 * 这个退化是静默的 —— 功能看起来正常，只有到线上被人刷满配额才会暴露成
 * 「所有人都注册不了」。用测试把两种维度的 key 形态钉住。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private RateLimitAspect aspect;

    /** 提供 by = IP 的注解实例（参数与 AuthController#guest 一致） */
    @RateLimit(key = "guest", maxRequests = 5, windowMillis = 7_200_000, by = RateLimit.By.IP)
    private void ipAnnotated() {
    }

    /** 提供默认 by = USER 的注解实例（参数与 FileController#upload 一致） */
    @RateLimit(key = "upload", maxRequests = 5, windowMillis = 60_000)
    private void userAnnotated() {
    }

    private RateLimit annotationOf(String methodName) throws Exception {
        return getClass().getDeclaredMethod(methodName).getAnnotation(RateLimit.class);
    }

    @AfterEach
    void tearDown() {
        // IpUtil 是静态可变状态，不复位会泄漏到别的测试
        IpUtil.configureTrustedProxies(List.of());
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** 模拟一个直连请求（无代理，无转发头）→ IpUtil 直接返回 remoteAddr */
    private void bindRequestFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void bindAnonymousAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    private String captureKey() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimiter).tryAcquire(keyCaptor.capture(), anyInt(), anyLong());
        return keyCaptor.getValue();
    }

    @Test
    void ipDimensionKeysByClientIpNotByAnonymousPrincipal() throws Throwable {
        // 匿名接口的真实场景：SecurityContext 里躺着 anonymousUser
        bindAnonymousAuth();
        bindRequestFrom("203.0.113.9");
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);

        aspect.around(joinPoint, annotationOf("ipAnnotated"));

        String key = captureKey();
        assertTrue(key.contains("203.0.113.9"), "key 应包含来源 IP，实际: " + key);
        assertFalse(key.contains("anonymousUser"),
                "by=IP 时绝不能落到 anonymousUser（那是全站共用配额的退化），实际: " + key);
        verify(joinPoint).proceed();
    }

    @Test
    void differentClientIpsGetIndependentQuotas() throws Throwable {
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        RateLimit annotation = annotationOf("ipAnnotated");

        bindRequestFrom("198.51.100.1");
        aspect.around(joinPoint, annotation);

        bindRequestFrom("198.51.100.2");
        aspect.around(joinPoint, annotation);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisRateLimiter, org.mockito.Mockito.times(2))
                .tryAcquire(keyCaptor.capture(), anyInt(), anyLong());
        List<String> keys = keyCaptor.getAllValues();
        assertNotEquals(keys.get(0), keys.get(1),
                "两个不同 IP 必须落到不同的 key，否则互相挤配额");
    }

    @Test
    void guestWindowConvertsTwoHoursToSeconds() throws Throwable {
        bindRequestFrom("203.0.113.9");
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);

        aspect.around(joinPoint, annotationOf("ipAnnotated"));

        // 2 小时窗口刻意与游客会话 TTL 对齐，换算错了并发上限的语义就跟着错
        ArgumentCaptor<Long> windowCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisRateLimiter).tryAcquire(anyString(), anyInt(), windowCaptor.capture());
        assertEquals(7200L, windowCaptor.getValue());
    }

    @Test
    void userDimensionStillKeysByUsername() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);

        aspect.around(joinPoint, annotationOf("userAnnotated"));

        String key = captureKey();
        assertTrue(key.contains("alice"), "默认 by=USER 应按用户名计数，实际: " + key);
        assertTrue(key.contains("upload"));
    }

    @Test
    void overLimitRejectsWithTooManyRequestsAndSkipsTarget() throws Throwable {
        bindRequestFrom("203.0.113.9");
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(joinPoint, annotationOf("ipAnnotated")));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void missingRequestContextFallsBackInsteadOfThrowing() throws Throwable {
        // 没有绑定请求上下文：退化为全局配额，但不能把正常请求打挂
        when(redisRateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);

        aspect.around(joinPoint, annotationOf("ipAnnotated"));

        assertTrue(captureKey().endsWith("unknown"));
        verify(joinPoint).proceed();
    }
}
