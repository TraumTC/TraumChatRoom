package com.tc.traumchatroom.aspect;

import com.tc.traumchatroom.dto.request.UpdatePasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationLogAspectTest {

    private final OperationLogAspect aspect = new OperationLogAspect();

    @Test
    void passwordRequestNeverWritesPasswordValues() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("old-secret-123");
        request.setNewPassword("new-secret-456");

        String detail = ReflectionTestUtils.invokeMethod(
                aspect, "buildDetail", new Object[]{request}, false, "旧密码错误");

        assertNotNull(detail);
        assertTrue(detail.contains("passwords-redacted"));
        assertFalse(detail.contains("old-secret-123"));
        assertFalse(detail.contains("new-secret-456"));
    }

    @Test
    void mapSensitiveKeysAreRedactedCaseInsensitively() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "new-name\nsecond-line");
        body.put("Password", "admin-reset-secret");
        body.put("accessToken", "access-secret");

        String detail = ReflectionTestUtils.invokeMethod(
                aspect, "buildDetail", new Object[]{body}, true, null);

        assertNotNull(detail);
        assertTrue(detail.contains("new-name"));
        assertTrue(detail.contains("new-name\\nsecond-line"));
        assertFalse(detail.contains("new-name\nsecond-line"));
        assertFalse(detail.contains("admin-reset-secret"));
        assertFalse(detail.contains("access-secret"));
    }
}
