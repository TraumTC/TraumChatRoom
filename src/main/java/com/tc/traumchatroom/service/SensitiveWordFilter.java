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
 * 二级处理策略：
 * - level=1：替换为 ***（默认）
 * - level=2：拦截，拒绝发送
 */
@Slf4j
@Service
public class SensitiveWordFilter {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    /**
     * 内存词库快照：Trie + 词数。
     * 一经发布即视为只读 —— 刷新走「另建新快照 → 整体替换引用」，绝不原地修改已发布的实例。
     */
    private record WordBook(SensitiveWordTrie trie, int wordCount) {}

    /**
     * 当前生效的词库快照。
     *
     * volatile 同时承担两件事：
     * 1. 可见性 —— 对该字段的写 happens-before 后续任意线程对它的读，
     *    因此新 Trie 内部（HashMap 节点网络）的构建结果能安全发布给读线程，
     *    不会出现读线程看到半初始化对象的情况；
     * 2. 原子性 —— 替换引用是单次赋值，读线程要么看到完整的旧快照、
     *    要么看到完整的新快照，不存在「已清空但尚未重建完」的中间态。
     *
     * 初值给一个空快照，保证 refresh() 之前或刷新失败时 filter() 也不会 NPE。
     */
    private volatile WordBook wordBook = new WordBook(new SensitiveWordTrie(), 0);

    /**
     * 启动时加载敏感词到内存
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新敏感词库（从数据库重新加载）。
     *
     * 原实现在共享的 Trie 上原地重建（先整体清空、再逐词 {@code addWord()}），
     * 改的正是读线程当时正在遍历的那个实例：
     * 从清空到重建完成的窗口内，{@code filter()} 匹配的是空/半成品 Trie，消息不经过滤
     * （WS 入站线程池 core 20 / max 50 并发读，词量大时窗口可达数十毫秒）；
     * 更糟的是 {@code findAll()} 一旦抛异常，词库会永久停在「已清空」状态，
     * 过滤功能直到下一次成功刷新前彻底失效。
     *
     * 现在先在旁边构建完整的新快照，最后一次性替换引用：读线程任何时刻看到的都是
     * 一个完整可用的词库；构建过程抛异常则原快照继续生效。
     *
     * synchronized 仅用于串行化并发刷新（管理员操作，极低频），读侧完全无锁。
     */
    public synchronized void refresh() {
        List<SensitiveWord> words = sensitiveWordMapper.findAll();
        SensitiveWordTrie newTrie = new SensitiveWordTrie();
        for (SensitiveWord word : words) {
            newTrie.addWord(word.getWord(), word.getLevel());
        }
        // 构建全部完成后才发布；在此之前读线程看到的仍是旧快照
        wordBook = new WordBook(newTrie, words.size());
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

        // 整趟过滤只读一次 volatile，锁定同一个快照。
        // 否则 matchFrom 与 getMatchedNode 可能落在刷新前后两棵不同的 Trie 上，
        // 出现「长度取自旧树、级别取自新树」的撕裂读。
        SensitiveWordTrie snapshot = wordBook.trie();

        StringBuilder result = new StringBuilder(content);
        boolean found = false;
        boolean blocked = false;
        int i = 0;

        while (i < content.length()) {
            int matchLength = snapshot.matchFrom(content, i);
            if (matchLength > 0) {
                found = true;
                SensitiveWordTrie.TrieNode node = snapshot.getMatchedNode(content, i, matchLength);

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
     * 获取当前内存中生效的敏感词数量。
     * 取自快照，不再每次全表 findAll()，且与实际加载进 Trie 的内容一致。
     */
    public int getWordCount() {
        return wordBook.wordCount();
    }
}
