package com.tc.traumchatroom.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveWordTrie 单元测试
 */
class SensitiveWordTrieTest {

    private SensitiveWordTrie trie;

    @BeforeEach
    void setUp() {
        trie = new SensitiveWordTrie();
    }

    @Test
    void matchesSimpleWord() {
        trie.addWord("广告", 1);
        assertThat(trie.matchFrom("投广告赚钱", 1)).isEqualTo(2);
        assertThat(trie.matchFrom("投广告赚钱", 0)).isZero();
    }

    @Test
    void matchesLongestWord() {
        // 同时存在"违禁"和"违禁品"，应从同一位置匹配最长的
        trie.addWord("违禁", 1);
        trie.addWord("违禁品", 2);
        assertThat(trie.matchFrom("卖违禁品", 1)).isEqualTo(3);
    }

    @Test
    void getMatchedNodeReturnsLevel() {
        trie.addWord("违禁", 2);
        SensitiveWordTrie.TrieNode node = trie.getMatchedNode("说违禁内容", 1, 2);
        assertThat(node).isNotNull();
        assertThat(node.getLevel()).isEqualTo(2);
        assertThat(node.getWord()).isEqualTo("违禁");
    }

    @Test
    void emptyWordIgnored() {
        trie.addWord(null, 1);
        trie.addWord("", 1);
        assertThat(trie.matchFrom("x", 0)).isZero();
    }
}
