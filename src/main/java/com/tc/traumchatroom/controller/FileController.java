package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.Idempotent;
import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.annotation.RateLimit;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.service.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
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

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
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

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedName)
                    .body(new FileSystemResource(file));

        } catch (Exception e) {
            log.error("文件下载失败: {}", request.getRequestURI(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
