package com.tc.traumchatroom.util;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;

import java.time.LocalDateTime;

/**
 * 消息构造器（Builder 模式）
 *
 * 为什么用 Builder？
 * - Message 字段多，构造函数参数过多可读性差
 * - 链式调用，代码更清晰
 * - 面试加分：展示设计模式理解
 *
 * 使用示例：
 * Message msg = new MessageBuilder()
 *     .sender(user)
 *     .content("Hello!")
 *     .type("text")
 *     .build();
 */
public class MessageBuilder {
    private final Message message = new Message();

    public MessageBuilder sender(User user) {
        if (user != null) {
            message.setSenderId(user.getId());
            message.setSenderName(user.getName());
        }
        return this;
    }

    public MessageBuilder content(String content) {
        message.setContent(content);
        return this;
    }

    public MessageBuilder type(String type) {
        message.setMessageType(type);
        return this;
    }

    public MessageBuilder file(String fileName, String filePath, Long fileSize) {
        message.setFileName(fileName);
        message.setFilePath(filePath);
        message.setFileSize(fileSize);
        return this;
    }

    public MessageBuilder receiver(User receiver) {
        if (receiver != null) {
            message.setReceiverId(receiver.getId());
            message.setReceiverName(receiver.getUsername());
        }
        return this;
    }

    public MessageBuilder aiReply(Long replyToId) {
        message.setIsAiReply(1);
        message.setReplyToId(replyToId);
        return this;
    }

    public Message build() {
        message.setCreatedAt(LocalDateTime.now());
        if (message.getIsAiReply() == null) message.setIsAiReply(0);
        if (message.getIsRecalled() == null) message.setIsRecalled(0);
        return message;
    }
}
