package com.tc.traumchatroom.handler;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件消息处理器
 */
@Component
public class FileMessageHandler implements MessageHandler {

    @Override
    public String getType() {
        return "file";
    }

    @Override
    public Message handle(MultipartFile file, User sender, String receiver, String content) {
        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setSenderName(sender.getName());
        message.setMessageType("file");
        message.setContent("");
        message.setIsAiReply(0);
        message.setIsRecalled(0);
        // 文件信息由 FileService 填充
        return message;
    }
}
