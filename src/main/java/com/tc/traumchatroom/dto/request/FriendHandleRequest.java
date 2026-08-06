package com.tc.traumchatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 处理好友申请请求 DTO
 */
@Data
public class FriendHandleRequest {

    @NotBlank(message = "操作不能为空")
    private String action;  // accept / reject
}
