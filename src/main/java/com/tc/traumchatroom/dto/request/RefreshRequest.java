package com.tc.traumchatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求 DTO
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
