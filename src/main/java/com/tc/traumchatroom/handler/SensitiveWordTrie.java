package com.tc.traumchatroom.handler;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Trie 树（前缀树）数据结构
 * 用于高效匹配敏感词，时间复杂度 O(n)，n 为文本长度
 *
 * 为什么用 Trie 树？
 * - 普通遍历：O(n*m)，n=文本长度，m=敏感词数量
 * - Trie 树：O(n)，与敏感词数量无关
 *
 * 线程模型：**构建期可写，发布后只读**。
 * 用法固定为「new → 逐词 addWord → 发布引用给读线程」，发布之后不得再调用 addWord。
 * 词库更新走整棵树重建 + 引用替换，见 SensitiveWordFilter#refresh。
 *
 * 因此这里不提供 clear() / setRoot() 之类的整体重置入口：
 * 它们会原地清掉正在被并发遍历的树，正是 P1-7「刷新期间过滤器完全失效」的成因。
 */
public class SensitiveWordTrie {

    /** 根节点（构建完成后不再替换） */
    private final TrieNode root = new TrieNode();

    /**
     * 添加敏感词
     * @param word 敏感词
     * @param level 处理级别：1替换 2拦截
     */
    public void addWord(String word, int level) {
        if (word == null || word.isEmpty()) return;

        TrieNode current = root;
        for (char c : word.toCharArray()) {
            current.getChildren().putIfAbsent(c, new TrieNode());
            current = current.getChildren().get(c);
        }
        current.setEnd(true);
        current.setLevel(level);
        current.setWord(word);
    }

    /**
     * 从指定位置开始匹配敏感词
     * @param text 文本
     * @param start 开始位置
     * @return 匹配到的敏感词长度，0 表示未匹配
     */
    public int matchFrom(String text, int start) {
        if (text == null || start >= text.length()) return 0;

        TrieNode current = root;
        int maxLength = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            current = current.getChildren().get(c);
            if (current == null) break;
            if (current.isEnd()) {
                maxLength = i - start + 1;
            }
        }
        return maxLength;
    }

    /**
     * 获取匹配到的敏感词信息
     */
    public TrieNode getMatchedNode(String text, int start, int length) {
        TrieNode current = root;
        for (int i = start; i < start + length; i++) {
            char c = text.charAt(i);
            current = current.getChildren().get(c);
            if (current == null) return null;
        }
        return current;
    }

    /**
     * Trie 节点
     */
    @Data
    public static class TrieNode {
        /** 子节点 */
        private Map<Character, TrieNode> children = new HashMap<>();
        /** 是否为敏感词结尾 */
        private boolean isEnd;
        /** 敏感词（仅在结尾节点有值） */
        private String word;
        /** 处理级别：1替换 2拦截 */
        private int level;
    }
}
