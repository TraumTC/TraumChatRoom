package com.tc.traumchatroom.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 敏感词实体 — 对应 sensitive_word 表
 */
@Data
public class SensitiveWord {
    /** 敏感词ID */
    private Integer id;
    /** 敏感词内容 */
    private String word;
    /** 处理级别：1替换为*** 2拦截拒绝发送 */
    private Integer level;
    /** 分类：insult(辱骂) / ad(广告) / spam(垃圾) */
    private String category;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
