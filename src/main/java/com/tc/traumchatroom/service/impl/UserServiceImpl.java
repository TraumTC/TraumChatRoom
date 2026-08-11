package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.config.FileStorageConfig;
import com.tc.traumchatroom.dto.request.UpdatePasswordRequest;
import com.tc.traumchatroom.dto.request.UpdateProfileRequest;
import com.tc.traumchatroom.dto.response.MentionableUserResponse;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.UserService;
import com.tc.traumchatroom.service.CacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Resource
    private CacheService cacheService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    // ---------- 修改个人资料 ----------

    @Override
    public void updateProfile(String username, UpdateProfileRequest request) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 检查游客权限
        if ("ROLE_GUEST".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "游客不能修改资料");
        }

        // 如果修改了昵称，检查唯一性
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(user.getName())) {
            User existing = userMapper.findByName(request.getName());
            if (existing != null) {
                throw new BusinessException(ErrorCode.NAME_EXISTS);
            }
            user.setName(request.getName());
        }

        userMapper.updateProfile(user);
        // 失效用户缓存，保证下次读取拿到最新昵称
        cacheService.evictUser(user.getId());
        log.info("用户 {} 修改资料成功", username);
    }

    // ---------- 修改密码 ----------

    @Override
    public void updatePassword(String username, UpdatePasswordRequest request) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "旧密码错误");
        }

        // 更新为新密码
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        userMapper.updatePassword(user.getId(), encodedNewPassword);
        log.info("用户 {} 修改密码成功", username);
    }

    // ---------- 上传头像 ----------

    /** 头像允许的图片扩展名 */
    private static final Set<String> AVATAR_ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** 头像允许的 MIME 类型 */
    private static final Set<String> AVATAR_ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");

    /** 头像压缩目标尺寸 */
    private static final int AVATAR_TARGET_SIZE = 256;

    /** 头像最小尺寸（防止恶意极小图片） */
    private static final int AVATAR_MIN_DIMENSION = 64;

    @Override
    public String uploadAvatar(String username, MultipartFile file) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 1. 校验文件不为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }

        // 2. 双重校验文件类型：扩展名 + MIME type
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String mimeType = file.getContentType();

        if (!AVATAR_ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的图片格式");
        }
        if (mimeType == null || !AVATAR_ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件类型不合法");
        }

        // 3. 校验文件大小（头像最大 5MB）
        long maxSize = parseSize(fileStorageConfig.getAvatarMaxSize());
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "头像大小不能超过 " + fileStorageConfig.getAvatarMaxSize());
        }

        // 4. 读取图片并校验尺寸
        BufferedImage sourceImage;
        try {
            sourceImage = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            log.error("头像图片读取失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取图片，文件可能已损坏");
        }
        if (sourceImage == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法解析图片，文件可能不是有效图片");
        }
        if (sourceImage.getWidth() < AVATAR_MIN_DIMENSION || sourceImage.getHeight() < AVATAR_MIN_DIMENSION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "图片尺寸不能小于 " + AVATAR_MIN_DIMENSION + "x" + AVATAR_MIN_DIMENSION);
        }

        // 5. 删除旧头像文件（如果有）
        if (StringUtils.hasText(user.getAvatar())) {
            deleteFile(user.getAvatar());
        }

        // 6. 生成文件名（带 userId 前缀，方便运维排查）
        String newFileName = "avatar_" + user.getId() + "_" + System.currentTimeMillis() +
                "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        String avatarDir = uploadDir + "avatars/";
        String filePath = avatarDir + newFileName;

        // 7. 服务端压缩：居中裁剪为 256x256 JPEG
        try {
            File dest = new File(filePath);
            dest.getParentFile().mkdirs();
            compressAndSaveAvatar(sourceImage, dest);
        } catch (IOException e) {
            log.error("头像压缩保存失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }

        // 8. 更新数据库
        String avatarUrl = "/api/file/download/avatars/" + newFileName;
        userMapper.updateAvatar(user.getId(), avatarUrl);

        // 9. 失效用户缓存，保证头像更新立即生效
        cacheService.evictUser(user.getId());

        log.info("用户 {} 上传头像成功: {}", username, newFileName);
        return avatarUrl;
    }

    /**
     * 服务端头像压缩：居中正方形裁剪 → 256x256 JPEG
     */
    private void compressAndSaveAvatar(BufferedImage source, File dest) throws IOException {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int minDim = Math.min(srcW, srcH);
        int sx = (srcW - minDim) / 2;
        int sy = (srcH - minDim) / 2;

        BufferedImage output = new BufferedImage(AVATAR_TARGET_SIZE, AVATAR_TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        // 白色背景（防止透明 PNG 转 JPEG 后变黑）
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, AVATAR_TARGET_SIZE, AVATAR_TARGET_SIZE);
        // 居中裁剪并绘制
        g.drawImage(source, 0, 0, AVATAR_TARGET_SIZE, AVATAR_TARGET_SIZE,
                sx, sy, sx + minDim, sy + minDim, null);
        g.dispose();

        ImageIO.write(output, "jpg", dest);
    }

    // ---------- 删除头像 ----------

    @Override
    public void deleteAvatar(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 删除文件（如果有）
        if (StringUtils.hasText(user.getAvatar())) {
            deleteFile(user.getAvatar());
        }

        // 数据库置 NULL
        userMapper.updateAvatar(user.getId(), null);

        // 失效用户缓存，保证头像删除立即生效
        cacheService.evictUser(user.getId());
        log.info("用户 {} 删除头像成功", username);
    }

    // ---------- 获取可@用户列表 ----------

    @Override
    public List<MentionableUserResponse> getMentionableUsers(String currentUsername) {
        // 搜索所有用户（排除自己，最多50条）
        List<User> users = userMapper.searchUsers("", getUserIdByUsername(currentUsername), 50);

        // 过滤掉与 AI 同名的真实用户，避免 @列表出现重复
        users = users.stream()
                .filter(u -> !"小爱".equals(u.getName()))
                .collect(Collectors.toList());

        List<MentionableUserResponse> result = users.stream()
                .map(u -> new MentionableUserResponse(u.getUsername(), u.getName(), u.getAvatar(), false))
                .collect(Collectors.toList());

        // 添加 AI 用户（小爱）
        result.add(new MentionableUserResponse("ai_xiaoai", "小爱", null, true));

        // 按昵称排序
        result.sort(Comparator.comparing(MentionableUserResponse::getName));

        return result;
    }

    // ---------- 辅助方法 ----------

    private Integer getUserIdByUsername(String username) {
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : -1;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 解析文件大小字符串（如 "5MB" → 字节数）
     */
    private long parseSize(String sizeStr) {
        sizeStr = sizeStr.toUpperCase().trim();
        if (sizeStr.endsWith("MB")) {
            return Long.parseLong(sizeStr.replace("MB", "").trim()) * 1024 * 1024;
        } else if (sizeStr.endsWith("KB")) {
            return Long.parseLong(sizeStr.replace("KB", "").trim()) * 1024;
        }
        return Long.parseLong(sizeStr);
    }

    /**
     * 删除文件（根据 avatarUrl 反推文件路径）
     * avatarUrl 格式: /api/file/download/avatars/xxx.jpg → 文件路径: uploads/avatars/xxx.jpg
     */
    private void deleteFile(String avatarUrl) {
        try {
            // 去掉 /api/file/download/ 前缀，保留子目录路径
            String relativePath = avatarUrl.replace("/api/file/download/", "");
            File file = new File(uploadDir + relativePath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            log.warn("删除旧头像文件失败: {}", avatarUrl, e);
        }
    }
}
