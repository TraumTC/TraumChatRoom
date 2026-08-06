package com.tc.traumchatroom.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送好友申请请求 DTO
 */
@Data
public class FriendApplyRequest {

    @NotNull(message = "接收者ID不能为空")
    private Integer receiverId;

    @Size(max = 100, message = "附言最多100字")
    private String message;
}
