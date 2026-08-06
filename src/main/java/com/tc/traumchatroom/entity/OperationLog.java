package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体 — 对应 operation_log 表
 */
@Data
public class OperationLog {
    /** 日志ID */
    private Long id;
    /** 操作者ID */
    private Integer userId;
    /** 操作者用户名（冗余，防止用户被删后无法查看） */
    private String username;
    /** 操作类型：LOGIN / LOGOUT / DELETE_USER 等 */
    private String action;
    /** 目标类型：user / message / file */
    private String targetType;
    /** 目标ID */
    private Long targetId;
    /** 操作详情（JSON格式） */
    private String detail;
    /** 操作者IP地址 */
    private String ip;
    /** 浏览器UA字符串 */
    private String userAgent;
    /** 操作时间 */
    private LocalDateTime createdAt;
}
