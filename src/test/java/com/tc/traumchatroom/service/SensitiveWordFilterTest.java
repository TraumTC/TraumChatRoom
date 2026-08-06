package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.SensitiveWord;
import com.tc.traumchatroom.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SensitiveWordFilter 单元测试（mock 数据库，验证三级处理策略）
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordFilterTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    @InjectMocks
    private SensitiveWordFilter filter;

    private SensitiveWord word(String text, int level) {
        SensitiveWord w = new SensitiveWord();
        w.setWord(text);
        w.setLevel(level);
        return w;
    }

    @BeforeEach
    void setUp() {
        // 预置敏感词库：广告(替换)、违禁(拦截)
        when(sensitiveWordMapper.findAll())
                .thenReturn(List.of(word("广告", 1), word("违禁", 2)));
        filter.refresh();
    }

    @Test
    void replaceLevelWordIsMasked() {
        FilterResult result = filter.filter("这是一个广告词");
        assertThat(result.isReplaced()).isTrue();
        assertThat(result.getContent()).doesNotContain("广告");
        assertThat(result.getContent()).contains("*");
    }

    @Test
    void blockLevelWordIsRejected() {
        FilterResult result = filter.filter("这是违禁内容");
        assertThat(result.isBlocked()).isTrue();
    }

    @Test
    void cleanContentPassesThrough() {
        FilterResult result = filter.filter("今天天气不错");
        assertThat(result.isReplaced()).isFalse();
        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getContent()).isEqualTo("今天天气不错");
    }

    @Test
    void nullOrEmptyContentPasses() {
        assertThat(filter.filter(null).isBlocked()).isFalse();
        assertThat(filter.filter("").isBlocked()).isFalse();
    }
}
