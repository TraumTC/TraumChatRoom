package com.tc.traumchatroom.config;

import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionUsesMappedHttpStatusAndKeepsBusinessCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<Result<?>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.PASSWORD_WRONG, "用户名或密码错误"), request);

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.PASSWORD_WRONG.getCode(), response.getBody().getCode());
        assertEquals("用户名或密码错误", response.getBody().getMessage());
    }

    @Test
    void serviceUnavailableBusinessExceptionReturns503() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");

        ResponseEntity<Result<?>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.SERVICE_UNAVAILABLE), request);

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), response.getBody().getCode());
    }
}
