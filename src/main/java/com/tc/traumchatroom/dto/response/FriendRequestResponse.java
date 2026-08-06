package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友申请响应 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendRequestResponse {
    private Long id;
    private SenderInfo sender;
    private ReceiverInfo receiver;
    private String message;
    private String status;      // pending / accepted / rejected / expired
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SenderInfo {
        private Integer id;
        private String username;
        private String name;
        private String avatar;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReceiverInfo {
        private Integer id;
        private String username;
        private String name;
        private String avatar;
    }
}
