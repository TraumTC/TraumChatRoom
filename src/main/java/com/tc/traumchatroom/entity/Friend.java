package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友关系实体 — 对应 friend 表（双向冗余存储）
 */
@Data
public class Friend {
    /** 记录ID */
    private Long id;
    /** 用户ID */
    private Integer userId;
    /** 好友ID */
    private Integer friendId;
    /** 好友备注名（A给B的备注） */
    private String remark;
    /** 成为好友时间 */
    private LocalDateTime createdAt;
}
