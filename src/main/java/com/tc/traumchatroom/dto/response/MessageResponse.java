package com.tc.traumchatroom.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private boolean aiReply;
    /** 是否已撤回 */
    private boolean recalled;
    /** 引用回复的消息ID */
    private Long replyToId;
    /** 发送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 发送者简要信息（嵌套对象）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SenderInfo {
        private Integer id;
        /** 用户名（唯一，前端用于会话区分） */
        private String username;
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
        /** 用户名（唯一，前端用于会话区分） */
        private String username;
        private String name;
    }
}
