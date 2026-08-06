package com.tc.traumchatroom.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务接口
 */
public interface FileService {

    /**
     * 上传文件并发送消息
     * @param file 文件
     * @param type 消息类型：image 或 file
     * @param receiver 接收者用户名（私聊时传，群聊为null）
     * @param senderUsername 发送者用户名
     * @return 文件信息（fileUrl, fileName, message）
     */
    Map<String, Object> uploadAndSendMessage(MultipartFile file, String type, String receiver, String senderUsername);

    /**
     * 获取文件的存储路径（用于下载）
     * @param fileName 文件名
     * @return 文件绝对路径
     */
    String getFilePath(String fileName);
}
