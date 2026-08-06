package com.tc.traumchatroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件上传配置 — 从 application.yml 读取 file.xxx 配置项
 */
@Configuration
@ConfigurationProperties(prefix = "file")
@Data
public class FileStorageConfig {
    /** 上传目录，如 uploads/ */
    private String uploadDir;
    /** 允许的文件类型，如 jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,zip,rar */
    private String allowedTypes;
    /** 头像最大大小，如 5MB */
    private String avatarMaxSize;
}
