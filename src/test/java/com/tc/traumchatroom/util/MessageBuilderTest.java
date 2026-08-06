package com.tc.traumchatroom.util;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MessageBuilder 单元测试
 */
class MessageBuilderTest {

    private User user(Long id, String username, String name) {
        User u = new User();
        u.setId(id.intValue());
        u.setUsername(username);
        u.setName(name);
        return u;
    }

    @Test
    void buildTextMessage() {
        User sender = user(1L, "zhangsan", "张三");
        Message msg = new MessageBuilder()
                .sender(sender)
                .content("你好")
                .type("text")
                .build();

        assertThat(msg.getSenderId()).isEqualTo(1);
        assertThat(msg.getSenderName()).isEqualTo("张三");
        assertThat(msg.getContent()).isEqualTo("你好");
        assertThat(msg.getMessageType()).isEqualTo("text");
        // 默认值
        assertThat(msg.getIsAiReply()).isZero();
        assertThat(msg.getIsRecalled()).isZero();
        assertThat(msg.getCreatedAt()).isNotNull();
    }

    @Test
    void buildAiReplyMessage() {
        User sender = user(1L, "zhangsan", "张三");
        Message msg = new MessageBuilder()
                .sender(sender)
                .content("你好！")
                .type("text")
                .aiReply(100L)
                .build();

        assertThat(msg.getIsAiReply()).isEqualTo(1);
        assertThat(msg.getReplyToId()).isEqualTo(100L);
    }

    @Test
    void buildFileMessage() {
        User sender = user(1L, "zhangsan", "张三");
        Message msg = new MessageBuilder()
                .sender(sender)
                .content("")
                .type("image")
                .file("a.jpg", "/api/file/download/a.jpg", 1024L)
                .build();

        assertThat(msg.getFileName()).isEqualTo("a.jpg");
        assertThat(msg.getFilePath()).isEqualTo("/api/file/download/a.jpg");
        assertThat(msg.getFileSize()).isEqualTo(1024L);
    }

    @Test
    void nullSenderIsSafe() {
        Message msg = new MessageBuilder().content("hi").type("text").build();
        assertThat(msg.getSenderId()).isNull();
        assertThat(msg.getSenderName()).isNull();
    }
}
