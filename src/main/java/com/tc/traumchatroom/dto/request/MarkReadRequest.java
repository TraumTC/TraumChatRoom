package com.tc.traumchatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标记私聊会话已读请求
 * POST /api/messages/read
 */
@Data
public class MarkReadRequest {
    /** 目标用户（会话对象）用户名 */
    @NotBlank(message = "目标用户不能为空")
    private String targetUsername;
}
