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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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

        // 2. 校验文件类型
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String allowedTypes = fileStorageConfig.getAllowedTypes();
        if (!Arrays.asList(allowedTypes.split(",")).contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        // 3. 校验文件大小（头像最大 5MB）
        long maxSize = parseSize(fileStorageConfig.getAvatarMaxSize());
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "头像大小不能超过 " + fileStorageConfig.getAvatarMaxSize());
        }

        // 4. 删除旧头像文件（如果有）
        if (StringUtils.hasText(user.getAvatar())) {
            deleteFile(user.getAvatar());
        }

        // 5. 生成唯一文件名并保存
        String newFileName = "avatar_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        String filePath = uploadDir + newFileName;

        try {
            File dest = new File(filePath);
            dest.getParentFile().mkdirs();  // 确保目录存在
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("头像保存失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }

        // 6. 更新数据库
        String avatarUrl = "/api/file/download/" + newFileName;
        userMapper.updateAvatar(user.getId(), avatarUrl);

        // 7. 失效用户缓存，保证头像更新立即生效
        cacheService.evictUser(user.getId());

        log.info("用户 {} 上传头像成功: {}", username, newFileName);
        return avatarUrl;
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
     */
    private void deleteFile(String avatarUrl) {
        try {
            // avatarUrl 格式: /api/file/download/xxx.jpg → 文件路径: uploads/xxx.jpg
            String fileName = avatarUrl.substring(avatarUrl.lastIndexOf("/") + 1);
            File file = new File(uploadDir + fileName);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            log.warn("删除旧头像文件失败: {}", avatarUrl, e);
        }
    }
}
