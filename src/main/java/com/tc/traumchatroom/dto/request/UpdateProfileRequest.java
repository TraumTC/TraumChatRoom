package com.tc.traumchatroom.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求 DTO
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 1, max = 20, message = "昵称长度需1-20字符")
    private String name;
}
