package com.tc.traumchatroom.handler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息处理器工厂
 *
 * Spring 会自动注入所有 MessageHandler 实现类到 handlers 列表
 * 通过 getType() 建立 类型→处理器 的映射
 *
 * 使用方式：
 *   MessageHandler handler = messageHandlerFactory.getHandler("text");
 *   Message message = handler.handle(null, sender, null, "Hello");
 */
@Slf4j
@Component
public class MessageHandlerFactory {

    @Resource
    private List<MessageHandler> handlers;

    private final Map<String, MessageHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (MessageHandler handler : handlers) {
            handlerMap.put(handler.getType(), handler);
            log.info("注册消息处理器: {}", handler.getType());
        }
    }

    /**
     * 根据消息类型获取对应的处理器
     * @param type 消息类型：text / image / file
     * @return 对应的处理器，未找到时返回 null
     */
    public MessageHandler getHandler(String type) {
        return handlerMap.get(type);
    }

    /**
     * 判断是否支持该消息类型
     */
    public boolean supports(String type) {
        return handlerMap.containsKey(type);
    }
}
