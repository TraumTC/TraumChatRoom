package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.SensitiveWord;
import com.tc.traumchatroom.handler.SensitiveWordTrie;
import com.tc.traumchatroom.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 敏感词过滤服务
 *
 * 核心设计：
 * 1. 启动时从数据库加载所有敏感词到 Trie 树
 * 2. 发送消息时用 Trie 树匹配（O(n) 时间复杂度）
 * 3. 支持动态刷新（管理员添加/删除敏感词后刷新内存）
 *
 * 三级处理策略：
 * - level=1：替换为 ***（默认）
 * - level=2：拦截，拒绝发送
 * - level=3：警告，仅记录日志
 */
@Slf4j
@Service
public class SensitiveWordFilter {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    /** 敏感词 Trie 树（内存中） */
    private final SensitiveWordTrie trie = new SensitiveWordTrie();

    /**
     * 启动时加载敏感词到内存
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新敏感词库（从数据库重新加载）
     */
    public void refresh() {
        trie.clear();
        List<SensitiveWord> words = sensitiveWordMapper.findAll();
        for (SensitiveWord word : words) {
            trie.addWord(word.getWord(), word.getLevel());
        }
        log.info("敏感词库已刷新，共加载 {} 个敏感词", words.size());
    }

    /**
     * 过滤文本中的敏感词
     * @param content 原始文本
     * @return 过滤结果
     */
    public FilterResult filter(String content) {
        if (content == null || content.isEmpty()) {
            return FilterResult.pass(content);
        }

        StringBuilder result = new StringBuilder(content);
        boolean found = false;
        boolean blocked = false;
        int i = 0;

        while (i < content.length()) {
            int matchLength = trie.matchFrom(content, i);
            if (matchLength > 0) {
                found = true;
                SensitiveWordTrie.TrieNode node = trie.getMatchedNode(content, i, matchLength);

                if (node != null && node.getLevel() == 2) {
                    // 拦截级别：直接拒绝
                    blocked = true;
                    break;
                }

                // 替换级别：替换为 *
                for (int j = i; j < i + matchLength; j++) {
                    result.setCharAt(j, '*');
                }
                i += matchLength;
            } else {
                i++;
            }
        }

        if (blocked) {
            return FilterResult.blocked("消息包含违规内容，已被拦截");
        }
        if (found) {
            return FilterResult.replaced(result.toString());
        }
        return FilterResult.pass(content);
    }

    /**
     * 获取当前敏感词数量
     */
    public int getWordCount() {
        return sensitiveWordMapper.findAll().size();
    }
}
