package com.tc.traumchatroom.service;

import com.tc.traumchatroom.handler.SensitiveWordTrie;

/**
 * 敏感词过滤结果
 */
public class FilterResult {
    private final boolean passed;      // 是否通过（无敏感词）
    private final boolean blocked;     // 是否被拦截
    private final boolean replaced;    // 是否被替换
    private final String content;      // 处理后的内容
    private final String message;      // 提示消息

    private FilterResult(boolean passed, boolean blocked, boolean replaced, String content, String message) {
        this.passed = passed;
        this.blocked = blocked;
        this.replaced = replaced;
        this.content = content;
        this.message = message;
    }

    public static FilterResult pass(String content) {
        return new FilterResult(true, false, false, content, null);
    }

    public static FilterResult blocked(String message) {
        return new FilterResult(false, true, false, null, message);
    }

    public static FilterResult replaced(String content) {
        return new FilterResult(false, false, true, content, null);
    }

    public boolean isPassed() { return passed; }
    public boolean isBlocked() { return blocked; }
    public boolean isReplaced() { return replaced; }
    public String getContent() { return content; }
    public String getMessage() { return message; }
}
