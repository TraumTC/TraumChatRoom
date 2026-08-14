package com.tc.traumchatroom.controller;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocketChatController.extractMentions 单元测试
 */
class WebSocketChatControllerTest {

    @Test
    void extractSimpleMentions() {
        Set<String> names = WebSocketChatController.extractMentions("@张三 你好 @李四 在吗");
        assertThat(names).containsExactly("张三", "李四");
    }

    @Test
    void deduplicateSameMention() {
        Set<String> names = WebSocketChatController.extractMentions("@张三 早上好 @张三 再见");
        assertThat(names).containsExactly("张三");
    }

    @Test
    void extractAcrossSentenceBoundaries() {
        Set<String> names = WebSocketChatController.extractMentions("问@王五 这个问题@赵六 怎么看");
        assertThat(names).containsExactly("王五", "赵六");
    }

    @Test
    void punctuationAfterNicknameIsNotPartOfMention() {
        Set<String> names = WebSocketChatController.extractMentions("@张三，你好！@alice. 在吗？");
        assertThat(names).containsExactly("张三", "alice");
    }

    @Test
    void supportsCommonUsernameCharacters() {
        Set<String> names = WebSocketChatController.extractMentions("@user_name @user-name @用户2");
        assertThat(names).containsExactly("user_name", "user-name", "用户2");
    }

    @Test
    void noMentionReturnsEmpty() {
        assertThat(WebSocketChatController.extractMentions("今天天气不错")).isEmpty();
        assertThat(WebSocketChatController.extractMentions(null)).isEmpty();
        assertThat(WebSocketChatController.extractMentions("")).isEmpty();
    }

    @Test
    void atSymbolWithoutTrailingNameIgnored() {
        // @ 后直接空白/结尾 → 不产生提及
        assertThat(WebSocketChatController.extractMentions("@ 单独符号")).isEmpty();
        assertThat(WebSocketChatController.extractMentions("结尾@")).isEmpty();
    }

    @Test
    void aiNicknameIsParsedLikeNormal() {
        // 小汤是合法昵称，会被解析出来（排除逻辑在 sendMentionNotices 内）
        Set<String> names = WebSocketChatController.extractMentions("@小汤 帮我算一下");
        assertThat(names).containsExactly("小汤");
    }
}
