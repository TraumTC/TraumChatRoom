package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.Idempotent;
import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.service.FileService;
import com.tc.traumchatroom.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文件控制器
 * 路径前缀：/api/file
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Resource
    private FileService fileService;

    /**
     * 上传文件
     * POST /api/file/upload
     * 幂等：防重复上传（配合 X-Request-Id header）
     */
    @Idempotent(key = "file-upload", timeout = 5)
    @RateLimit(key = "upload", maxRequests = 5, windowMillis = 60000)
    @LogOperation(action = "UPLOAD_FILE", targetType = "file")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(value = "receiver", required = false) String receiver,
            HttpServletRequest request) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String clientIp = com.tc.traumchatroom.util.IpUtil.fromHttp(request);
        Map<String, Object> result = fileService.uploadAndSendMessage(file, type, receiver, username, clientIp);
        return Result.success(result);
    }

    /**
     * 下载文件
     * GET /api/file/download/**  支持子目录如 /api/file/download/avatars/xxx.png
     *
     * 缓存策略：文件名由「时间戳 + UUID」生成，内容一经写入永不变更（头像更新也是换新文件名 +
     * 删旧文件），因此可以安全地下发一年期 immutable 强缓存 + ETag。
     * 这一步对移动端尤其关键：聊天列表是虚拟滚动，图片/头像的 <img> 会随列表回收反复重建，
     * 没有缓存头时每次重建都要重新走网络下载整张原图。
     *
     * 展示方式：图片/音视频用 inline（供 <img>/<video> 内联展示），其余类型仍用 attachment；
     * 带 ?name= 的请求视为显式下载，一律 attachment。
     */
    @GetMapping("/download/**")
    public ResponseEntity<FileSystemResource> download(HttpServletRequest request,
                                                        @RequestParam(value = "name", required = false) String displayName) {
        try {
            // 从 URI 中提取文件路径（去掉 /api/file/download/ 前缀）
            String uri = request.getRequestURI();
            String prefix = "/api/file/download/";
            int idx = uri.indexOf(prefix);
            if (idx < 0) {
                return ResponseEntity.badRequest().build();
            }
            String fileName = uri.substring(idx + prefix.length());

            String filePath = fileService.getFilePath(fileName);
            File file = new File(filePath);

            if (!file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            long lastModified = file.lastModified();
            // 弱 ETag 由 修改时间 + 长度 组成，足以区分同名不同内容（实际上文件名唯一，不会发生）
            String etag = "\"" + lastModified + "-" + file.length() + "\"";
            CacheControl cacheControl = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

            // 条件请求命中 → 304，不回传文件体
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .lastModified(lastModified)
                        .cacheControl(cacheControl)
                        .build();
            }

            // 检测文件类型
            Path path = file.toPath();
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 使用原始文件名（如果有），否则用存储文件名
            String downloadName = (displayName != null && !displayName.isEmpty()) ? displayName : fileName;

            // URL 编码文件名（HTTP 头不能包含非 ASCII 字符）
            String encodedName = java.net.URLEncoder.encode(downloadName, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");

            // 带 ?name= 视为显式下载；否则可内联的媒体类型用 inline
            boolean explicitDownload = displayName != null && !displayName.isEmpty();
            String disposition = (!explicitDownload && isInlineSafe(contentType)) ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(cacheControl)
                    // 禁止 MIME 嗅探：inline 下发时避免浏览器把文件猜成可执行类型
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename*=UTF-8''" + encodedName)
                    .body(new FileSystemResource(file));

        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException | IllegalArgumentException e) {
            log.error("文件下载失败: {}", request.getRequestURI(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 是否可以安全地以 inline 下发。
     * 只放通位图与音视频；svg 走 attachment（内联 SVG 可执行脚本 → 存储型 XSS）。
     */
    static boolean isInlineSafe(String contentType) {
        if (contentType == null) return false;
        String type = contentType.toLowerCase();
        if (type.startsWith("image/svg")) return false;
        return type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/");
    }
}
