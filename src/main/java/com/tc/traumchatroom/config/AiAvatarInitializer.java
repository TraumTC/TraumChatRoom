package com.tc.traumchatroom.config;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.CacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 助手（小汤）头像启动同步器
 *
 * 启动时读取 ai.api.avatar-url 配置，校验头像文件存在且为合法图片后，
 * 将头像 URL 同步写入数据库 ai_xiaoai 用户的 avatar 字段（数据库成为唯一权威源，
 * 历史消息/实时消息/@列表三处读取统一正确）。
 *
 * 规则：
 * - 配置为空 → 清空数据库头像（恢复默认首字头像）
 * - 配置非空且文件存在 + ImageIO 可解码 → 落库
 * - 配置非空但文件不存在 / 非合法图片 / 读取异常 → 仅告警，不动数据库（防坏配置污染）
 * 每个分支均写入日志便于排查。
 */
@Slf4j
@Component
public class AiAvatarInitializer implements ApplicationRunner {

    private static final String AI_USERNAME = "ai_XiaoTang";
    private static final String DOWNLOAD_PREFIX = "/api/file/download/";

    @Resource
    private UserMapper userMapper;

    @Resource
    private AiConfig aiConfig;

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Resource
    private CacheService cacheService;

    @Override
    public void run(ApplicationArguments args) {
        User aiUser = userMapper.findByUsername(AI_USERNAME);
        if (aiUser == null) {
            log.warn("[AI头像] {} 用户不存在，跳过头像同步", AI_USERNAME);
            return;
        }

        String avatarUrl = aiConfig.getAvatarUrl();

        // 配置为空 → 回到默认首字头像（清库）
        if (avatarUrl == null || avatarUrl.isBlank()) {
            if (aiUser.getAvatar() != null) {
                userMapper.updateAvatar(aiUser.getId(), null);
                cacheService.evictUser(aiUser.getId());
                log.info("[AI头像] 配置为空，已清空数据库头像（恢复默认首字头像）");
            } else {
                log.debug("[AI头像] 配置为空且数据库本就无头像，无需处理");
            }
            return;
        }

        // 反推磁盘路径：/api/file/download/avatars/ai/x.jpg → uploads/avatars/ai/x.jpg
        String relativePath = avatarUrl.replace(DOWNLOAD_PREFIX, "");
        Path uploadRoot = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
        Path diskPath = uploadRoot.resolve(relativePath).normalize();
        File file = diskPath.toFile();

        if (!file.exists() || !file.isFile()) {
            log.warn("[AI头像] 配置文件不存在，跳过落库：url={}, diskPath={}", avatarUrl, diskPath);
            return;
        }

        // 校验为合法图片
        try (InputStream in = new FileInputStream(file)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                log.warn("[AI头像] 文件不是有效图片，跳过落库：url={}, diskPath={}", avatarUrl, diskPath);
                return;
            }
        } catch (IOException e) {
            log.warn("[AI头像] 图片读取失败，跳过落库：url={}, diskPath={}", avatarUrl, diskPath, e);
            return;
        }

        // 校验通过 → 落库（与数据库当前值不同才写，避免无谓 UPDATE）
        if (!avatarUrl.equals(aiUser.getAvatar())) {
            userMapper.updateAvatar(aiUser.getId(), avatarUrl);
            cacheService.evictUser(aiUser.getId());
            log.info("[AI头像] 头像同步完成：{} -> {}", aiUser.getUsername(), avatarUrl);
        } else {
            log.debug("[AI头像] 数据库头像与配置一致，无需更新：{}", avatarUrl);
        }
    }
}
