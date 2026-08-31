package com.tc.traumchatroom.service.impl;

import com.tc.traumchatroom.config.FileStorageConfig;
import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.handler.MessageHandler;
import com.tc.traumchatroom.handler.MessageHandlerFactory;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private MessageHandlerFactory messageHandlerFactory;

    @Resource
    private com.tc.traumchatroom.mapper.FriendMapper friendMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public Map<String, Object> uploadAndSendMessage(MultipartFile file, String type,
                                                     String receiver, String senderUsername, String clientIp) {
        // 1. 校验文件
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

        // 3. 校验文件大小
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        // 4. 生成唯一文件名
        String newFileName = System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        // 5. 保存文件
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path targetPath = uploadPath.resolve(newFileName);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        // 6. 查询发送者
        User sender = userMapper.findByUsername(senderUsername);
        if (sender == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 6.1 游客仅允许上传图片（防止游客上传脚本/压缩包等）
        if ("ROLE_GUEST".equals(sender.getRole())) {
            Set<String> imageTypes = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
            if (!imageTypes.contains(extension.toLowerCase())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "游客仅支持上传图片");
            }
        }

        // 7. 使用策略模式构造消息
        MessageHandler handler = messageHandlerFactory.getHandler(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的消息类型: " + type);
        }
        Message message = handler.handle(file, sender, receiver, "");

        // 8. 填充文件信息
        message.setFileName(originalName);
        message.setFilePath("/api/file/download/" + newFileName);
        message.setFileSize(file.getSize());

        // 9. 私聊时设置接收者并校验好友关系
        if (StringUtils.hasText(receiver)) {
            User receiverUser = userMapper.findByUsername(receiver);
            if (receiverUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "接收者不存在");
            }
            // 私聊文件仅限好友关系，且不得泄露到群聊
            if (!friendMapper.exists(sender.getId(), receiverUser.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能向好友发送私聊文件");
            }
            message.setReceiverId(receiverUser.getId());
            message.setReceiverName(receiverUser.getUsername());
        }

        // 10. 保存消息
        message.setCreatedAt(LocalDateTime.now());
        message.setSenderIp(clientIp);
        messageMapper.insert(message);

        // 11. 构造响应
        MessageResponse msgResponse = toMessageResponse(message, sender);

        // 11. 广播消息
        if (message.getReceiverId() != null) {
            // 私聊
            messagingTemplate.convertAndSendToUser(receiver, "/queue/private-messages", msgResponse);
            messagingTemplate.convertAndSendToUser(senderUsername, "/queue/private-messages", msgResponse);
        } else {
            // 群聊
            messagingTemplate.convertAndSend("/topic/messages", msgResponse);
        }

        log.info("用户 {} 上传文件: {} ({})", senderUsername, originalName, type);

        return Map.of(
                "message", msgResponse,
                "fileUrl", message.getFilePath(),
                "fileName", originalName
        );
    }

    @Override
    public String getFilePath(String fileName) {
        // 路径遍历防护：normalize 后必须仍位于上传根目录内
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名为空");
        }
        Path resolved = uploadRoot.resolve(fileName).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        return resolved.toString();
    }

    private String getFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private MessageResponse toMessageResponse(Message msg, User sender) {
        MessageResponse response = new MessageResponse();
        response.setId(msg.getId());
        response.setContent(msg.getContent());
        response.setMessageType(msg.getMessageType());
        response.setFileName(msg.getFileName());
        response.setFilePath(msg.getFilePath());
        response.setFileSize(msg.getFileSize());
        response.setAiReply(false);
        response.setRecalled(false);
        response.setCreatedAt(msg.getCreatedAt());

        // username 必须回传：前端以 username 作为私聊会话唯一标识，缺失会退化成按昵称建会话，导致同一个人出现多个页签
        MessageResponse.SenderInfo senderInfo = new MessageResponse.SenderInfo();
        senderInfo.setId(sender.getId());
        senderInfo.setUsername(sender.getUsername());
        senderInfo.setName(sender.getName());
        senderInfo.setAvatar(sender.getAvatar());
        response.setSender(senderInfo);

        // 接收者信息（私聊时，receiver_name 语义为 username）
        if (msg.getReceiverId() != null) {
            MessageResponse.ReceiverInfo receiverInfo = new MessageResponse.ReceiverInfo();
            receiverInfo.setId(msg.getReceiverId());
            receiverInfo.setUsername(msg.getReceiverName());
            receiverInfo.setName(msg.getReceiverName());
            response.setReceiver(receiverInfo);
        }

        return response;
    }
}
