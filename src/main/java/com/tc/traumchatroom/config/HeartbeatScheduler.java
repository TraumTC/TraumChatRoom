
package com.tc.traumchatroom.config;

import com.tc.traumchatroom.util.OnlineUserUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@EnableScheduling
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    @Resource
    private OnlineUserUtil onlineUserUtil;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 30000)
    public void broadcastOnlineUsers() {
        Set<String> onlineUsers = onlineUserUtil.getOnlineUsers();
        messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
        log.debug("定期广播在线用户，当前在线: {}", onlineUsers);
    }
}
