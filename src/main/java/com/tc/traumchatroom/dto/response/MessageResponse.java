package com.tc.traumchatroom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息响应 DTO
 * 对应接口文档中的消息对象结构
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
    /** 消息ID */
    private Long id;
    /** 发送者信息 */
    private SenderInfo sender;
    /** 接收者信息（私聊时有值） */
    private ReceiverInfo receiver;
    /** 消息内容 */
    private String content;
    /** 消息类型：text/file/image */
    private String messageType;
    /** 文件名（文件消息时有值） */
    private String fileName;
    /** 文件URL（文件消息时有值） */
    private String filePath;
    /** 文件大小（文件消息时有值） */
    private Long fileSize;
    /** 是否为AI回复 */
    private boolean isAiReply;
    /** 是否已撤回 */
    private boolean isRecalled;
    /** 引用回复的消息ID */
    private Long replyToId;
    /** 发送时间 */
    private LocalDateTime createdAt;

    /**
     * 发送者简要信息（嵌套对象）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SenderInfo {
        private Integer id;
        private String name;
        private String avatar;
    }

    /**
     * 接收者简要信息（嵌套对象，私聊时用）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReceiverInfo {
        private Integer id;
        private String name;
    }
}
