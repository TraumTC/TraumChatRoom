package com.tc.traumchatroom.dto.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentionNoticeVOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializeStoredMentionNoticeWithTypeField() throws Exception {
        String json = """
                {
                  "type": "mention",
                  "senderUsername": "sender01",
                  "senderName": "发送者",
                  "messageId": 123,
                  "content": "@接收者 你好",
                  "createdAt": "2026-08-13T20:54:26"
                }
                """;

        MentionNoticeVO notice = objectMapper.readValue(json, MentionNoticeVO.class);

        assertThat(notice.getSenderUsername()).isEqualTo("sender01");
        assertThat(notice.getSenderName()).isEqualTo("发送者");
        assertThat(notice.getMessageId()).isEqualTo(123L);
        assertThat(notice.getContent()).isEqualTo("@接收者 你好");
        assertThat(notice.getCreatedAt()).isEqualTo("2026-08-13T20:54:26");
    }
}
