package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息实体 — 对应 message 表
 */
@Data
public class Message {
    /** 消息ID，BIGINT防溢出 */
    private Long id;
    /** 发送者ID，用户删除时置NULL */
    private Integer senderId;
    /** 发送者昵称（冗余字段，避免JOIN） */
    private String senderName;
    /** 接收者ID，群聊时为NULL */
    private Integer receiverId;
    /** 接收者昵称（冗余字段） */
    private String receiverName;
    /** 消息内容 */
    private String content;
    /** 消息类型：text / file / image / system */
    private String messageType;
    /** 原始文件名 */
    private String fileName;
    /** 文件存储路径 */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 是否为AI回复：0否 1是 */
    private Integer isAiReply;
    /** 引用回复的消息ID */
    private Long replyToId;
    /** 是否已撤回：0否 1是 */
    private Integer isRecalled;
    /** 撤回时间 */
    private LocalDateTime recalledAt;
    /** 撤回前的原始内容（管理员可查看） */
    private String originalContent;
    /** 发送者IP地址 */
    private String senderIp;
    /** 发送时间 */
    private LocalDateTime createdAt;
    /** 软删除时间 */
    private LocalDateTime deletedAt;
}
