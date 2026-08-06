package com.tc.traumchatroom.handler;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文本消息处理器
 */
@Component
public class TextMessageHandler implements MessageHandler {

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public Message handle(MultipartFile file, User sender, String receiver, String content) {
        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setSenderName(sender.getName());
        message.setMessageType("text");
        message.setContent(content != null ? content : "");
        message.setIsAiReply(0);
        message.setIsRecalled(0);
        return message;
    }
}
