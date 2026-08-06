package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友申请实体 — 对应 friend_request 表
 */
@Data
public class FriendRequest {
    /** 申请ID */
    private Long id;
    /** 申请者ID */
    private Integer senderId;
    /** 接收者ID */
    private Integer receiverId;
    /** 申请附言 */
    private String message;
    /** 状态：0待处理 1已同意 2已拒绝 */
    private Integer status;
    /** 申请时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
