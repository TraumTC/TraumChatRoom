package com.tc.traumchatroom.handler;

import com.tc.traumchatroom.entity.Message;
import com.tc.traumchatroom.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * 消息处理器接口（策略模式）
 *
 * 为什么用策略模式？
 * - 如果用 if-else 判断消息类型，新增类型时要修改原有代码（违反开闭原则）
 * - 策略模式：新增类型只需加一个 Handler 类，不改已有代码
 * - 面试加分：展示你对设计模式的理解
 *
 * 使用方式：
 * 1. 实现此接口，标注 @Component
 * 2. 在 Map<String, MessageHandler> 中自动注入所有实现
 * 3. 根据消息类型选择对应的 Handler
 */
public interface MessageHandler {

    /**
     * 获取此处理器处理的消息类型
     * @return "text" / "image" / "file"
     */
    String getType();

    /**
     * 处理消息
     * @param file 上传的文件（文本消息时为 null）
     * @param sender 发送者
     * @param receiver 接收者用户名（私聊时有值）
     * @param content 消息内容（文本消息时有值）
     * @return 构造好的消息实体
     */
    Message handle(MultipartFile file, User sender, String receiver, String content);
}
