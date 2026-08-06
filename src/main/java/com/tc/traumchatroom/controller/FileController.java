package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.Idempotent;
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
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(value = "receiver", required = false) String receiver) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> result = fileService.uploadAndSendMessage(file, type, receiver, username);
        return Result.success(result);
    }

    /**
     * 下载文件
     * GET /api/file/download/{fileName}
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String fileName) {
        try {
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

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(file));

        } catch (Exception e) {
            log.error("文件下载失败: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
