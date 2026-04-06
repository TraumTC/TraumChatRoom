package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.config.FileStorageConfig;
import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.ChatService;
import com.tc.traumchatroom.util.OnlineUserUtil;
import com.tc.traumchatroom.util.UserUtil;
//import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileStorageConfig fileStorageConfig;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserUtil userUtil;

    @Autowired
    private OnlineUserUtil onlineUserUtil;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Path fileStorageLocation;

    public FileController(FileStorageConfig fileStorageConfig) {
        this.fileStorageLocation = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("无法创建文件存储目录", ex);
        }
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "receiver", required = false) String receiver,
                                          @RequestParam("type") String messageType,
                                          HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            User currentUser = userUtil.getCurrentUser();
            if (currentUser == null) {
                Object guestUser = request.getSession().getAttribute("GUEST_USER");
                if (guestUser instanceof User) {
                    currentUser = (User) guestUser;
                }
            }

            if (currentUser == null) {
                result.put("success", false);
                result.put("message", "用户未登录");
                return result;
            }

            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件不能为空");
                return result;
            }

            if (file.getSize() > fileStorageConfig.getMaxFileSize()) {
                result.put("success", false);
                result.put("message", "文件大小不能超过100MB");
                return result;
            }

            String originalFileName = file.getOriginalFilename();
            String extension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String uniqueFileName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/file/download/" + uniqueFileName;
            Message message = new Message();
            message.setSenderId(currentUser.getId());
            message.setSender(currentUser.getName());
            message.setReceiver(receiver);
            message.setMessage(messageType.equals("file") ? "[文件] " + originalFileName : "[图片] " + originalFileName);
            message.setSendTime(LocalDateTime.now());
            message.setFileName(originalFileName);
            message.setFilePath(fileUrl);
            message.setFileSize(file.getSize());
            message.setMessageType(messageType);

            chatService.saveMessage(message);

            if (receiver != null && !receiver.isEmpty()) {
                String receiverUsername = onlineUserUtil.getUsernameByName(receiver);
                if (receiverUsername != null) {
                    messagingTemplate.convertAndSendToUser(receiverUsername, "/queue/private-messages", message);
                }
                messagingTemplate.convertAndSendToUser(currentUser.getUsername(), "/queue/private-messages", message);
            } else {
                messagingTemplate.convertAndSend("/topic/messages", message);
            }

            result.put("success", true);
            result.put("message", message);
            result.put("fileUrl", fileUrl);
            result.put("fileName", originalFileName);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                try {
                    contentType = Files.probeContentType(filePath);
                } catch (IOException ex) {
                    log.warn("无法检测文件类型", ex);
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            log.error("文件路径错误", ex);
            return ResponseEntity.badRequest().build();
        }
    }
}
