package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.config.FileStorageConfig;
import com.tc.traumchatroom.config.AiConfig;
import com.tc.traumchatroom.dto.request.UpdatePasswordRequest;
import com.tc.traumchatroom.dto.request.UpdateProfileRequest;
import com.tc.traumchatroom.dto.response.MentionableUserResponse;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.service.UserService;
import com.tc.traumchatroom.service.CacheService;
import com.tc.traumchatroom.service.RefreshTokenStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Resource
    private AiConfig aiConfig;

    @Resource
    private CacheService cacheService;

    @Resource
    private RefreshTokenStore refreshTokenStore;

    @Value("${file.upload-dir}")
    private String uploadDir;

    // ---------- 修改个人资料 ----------

    @Override
    @Transactional
    public void updateProfile(String username, UpdateProfileRequest request) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 检查游客权限
        if ("ROLE_GUEST".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "游客不能修改资料");
        }

        // 如果修改了昵称，检查唯一性（含已软删除用户，软删除后昵称永久保留）
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(user.getName())) {
            User existing = userMapper.findByNameIncludingDeleted(request.getName());
            // 排除当前用户自己：MySQL 默认 collation 不区分大小写，查重会命中自身的旧昵称
            //（如 Alice → alice），需要允许这种仅大小写变化的修改
            if (existing != null && !existing.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.NAME_EXISTS);
            }
            String oldName = user.getName();
            String newName = request.getName();
            user.setName(newName);

            userMapper.updateProfile(user);

            // 同步更新 message 表中该用户发送的消息的冗余昵称字段
            // （receiver_name 存的是 username 不受影响，只需更新 sender_name）
            int updated = messageMapper.updateSenderName(user.getId(), oldName, newName);
            log.info("用户 {} 改昵称: {} -> {}，同步更新 {} 条消息的 sender_name", username, oldName, newName, updated);
        } else {
            userMapper.updateProfile(user);
        }

        // 失效用户缓存，保证下次读取拿到最新昵称
        cacheService.evictUserAfterCommit(user.getId());
        log.info("用户 {} 修改资料成功", username);
    }

    // ---------- 修改密码 ----------

    @Override
    @Transactional
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
        refreshTokenStore.revokeAll(username);
        log.info("用户 {} 修改密码成功", username);
    }

    // ---------- 上传头像 ----------

    /** 头像允许的图片扩展名 */
    private static final Set<String> AVATAR_ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** 头像允许的 MIME 类型 */
    private static final Set<String> AVATAR_ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");

    /**
     * 头像允许的「真实」图片格式（ImageReader#getFormatName 归一化为小写）。
     * 扩展名与 Content-Type 都由客户端提供、可任意伪造，只有 reader 识别出的格式可信。
     */
    private static final Set<String> AVATAR_ALLOWED_FORMATS =
            Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** 头像压缩目标尺寸 */
    private static final int AVATAR_TARGET_SIZE = 256;

    /** 头像最小尺寸（防止恶意极小图片） */
    private static final int AVATAR_MIN_DIMENSION = 64;

    /** 头像单边最大尺寸（防止畸形超长图片） */
    private static final int AVATAR_MAX_DIMENSION = 10000;

    /** 头像最大像素总量（4000 万像素，覆盖主流高像素相机原图） */
    private static final long AVATAR_MAX_PIXELS = 40_000_000L;

    /**
     * 单次解码的像素预算（400 万像素 ≈ 16 MB TYPE_INT_ARGB）。
     * 超出则提高降采样倍率，使堆占用与源图分辨率解耦。
     */
    private static final long AVATAR_DECODE_PIXEL_BUDGET = 4_000_000L;

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

        // 4. 读取图片：先验 header 尺寸，再降采样解码（防解压炸弹，见 readAvatarImage）
        BufferedImage sourceImage = readAvatarImage(file);

        // 5. 删除旧头像文件（如果有）
        if (StringUtils.hasText(user.getAvatar())) {
            deleteFile(user.getAvatar());
        }

        // 6. 生成文件名（带 userId 前缀，方便运维排查）
        String newFileName = "avatar_" + user.getId() + "_" + System.currentTimeMillis() +
                "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        String avatarDir = Paths.get(uploadDir, "avatars").toString() + File.separator;
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
     * 安全读取头像图片。
     *
     * 直接 {@code ImageIO.read()} 会先把整张图解进堆里、之后才轮到尺寸校验 —— 一张 5 MB 的
     * 高压缩比 PNG 可以解出 30000x30000 的 BufferedImage（TYPE_INT_RGB ≈ 3.6 GB），
     * 单个请求即可打爆 JVM。这里把顺序倒过来，分三层防护：
     * <ol>
     *   <li>校验 reader 识别出的真实格式，堵住「.png 扩展名 + image/png 头裹一个 TIFF」
     *       这类绕过格式白名单、换用重量级解码器的路径；</li>
     *   <li>只读 header 取 width/height 判断像素总量，超限直接拒绝，全程不分配像素缓冲；</li>
     *   <li>用 setSourceSubsampling 在解码阶段就抽样，让解码结果落在像素预算内，
     *       堆占用与源图分辨率解耦（恒定 MB 级）。</li>
     * </ol>
     */
    private BufferedImage readAvatarImage(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取图片，文件可能已损坏");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无法解析图片，文件可能不是有效图片");
            }

            ImageReader reader = readers.next();
            try {
                // ignoreMetadata=true：不解析 EXIF 等元数据，减少攻击面
                reader.setInput(input, true, true);

                String format = reader.getFormatName();
                if (format == null || !AVATAR_ALLOWED_FORMATS.contains(format.toLowerCase())) {
                    throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的图片格式");
                }

                // 此处只读 header，尚未解码像素
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width < AVATAR_MIN_DIMENSION || height < AVATAR_MIN_DIMENSION) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "图片尺寸不能小于 " + AVATAR_MIN_DIMENSION + "x" + AVATAR_MIN_DIMENSION);
                }
                if (width > AVATAR_MAX_DIMENSION || height > AVATAR_MAX_DIMENSION
                        || (long) width * height > AVATAR_MAX_PIXELS) {
                    throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                            "图片分辨率过大，单边不能超过 " + AVATAR_MAX_DIMENSION
                                    + "px 且总像素不能超过 " + (AVATAR_MAX_PIXELS / 10_000L) + " 万");
                }

                ImageReadParam param = reader.getDefaultReadParam();
                int sampling = subsamplingFactor(width, height);
                if (sampling > 1) {
                    param.setSourceSubsampling(sampling, sampling, 0, 0);
                }

                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "无法解析图片，文件可能不是有效图片");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 畸形图片会让各家 reader 抛出五花八门的非受检异常（数组越界、负数组长度等），
            // 一并归为「文件损坏」的 400，避免用户输入把栈打到 500
            log.warn("头像图片读取失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取图片，文件可能已损坏");
        }
    }

    /**
     * 计算解码降采样倍率，同时满足两项诉求：
     * <ul>
     *   <li>质量：解码结果最短边保持 ≥ 2 倍目标尺寸，留足缩放余量；</li>
     *   <li>内存：解码结果总像素不超过预算 —— 对 10000x600 这类畸形长条图，
     *       内存诉求会覆盖质量诉求（居中裁剪本来也只用到中间那一小块）。</li>
     * </ul>
     */
    private static int subsamplingFactor(int width, int height) {
        int sampling = Math.max(1, Math.min(width, height) / (AVATAR_TARGET_SIZE * 2));
        while ((long) Math.ceilDiv(width, sampling) * Math.ceilDiv(height, sampling)
                > AVATAR_DECODE_PIXEL_BUDGET) {
            sampling++;
        }
        return sampling;
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
        // 双线性插值：解码阶段的降采样是点抽样，缩放这一步补上平滑，避免头像出现锯齿
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
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
                .filter(u -> !"小汤".equals(u.getName()))
                .collect(Collectors.toList());

        List<MentionableUserResponse> result = users.stream()
                .map(u -> new MentionableUserResponse(u.getUsername(), u.getName(), u.getAvatar(), false))
                .collect(Collectors.toList());

        // 添加 AI 用户（小汤，头像走配置，为空则前端显示默认头像）
        result.add(new MentionableUserResponse("ai_xiaoai", "小汤", aiConfig.getAvatarUrl(), true));

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
            File file = new File(Paths.get(uploadDir, relativePath).toString());
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            log.warn("删除旧头像文件失败: {}", avatarUrl, e);
        }
    }
}
