
package com.tc.traumchatroom.config;

import com.tc.traumchatroom.util.OnlineUserUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@EnableScheduling
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private static final long HEARTBEAT_TIMEOUT = 45000;

    @Resource
    private OnlineUserUtil onlineUserUtil;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 30000)
    public void broadcastOnlineUsers() {
        checkAndRemoveTimeoutUsers();

        Set<String> onlineUsers = onlineUserUtil.getOnlineUsers();
        messagingTemplate.convertAndSend("/topic/onlineUsers", onlineUsers);
        log.debug("定期广播在线用户，当前在线: {}, 在线人数: {}", onlineUsers, onlineUsers.size());
    }

    private void checkAndRemoveTimeoutUsers() {
        Map<String, Long> usersWithTime = onlineUserUtil.getOnlineUsersWithTime();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : usersWithTime.entrySet()) {
            String username = entry.getKey();
            Long lastHeartbeat = entry.getValue();

            if (currentTime - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                String name = onlineUserUtil.getNameByUsername(username);
                onlineUserUtil.removeUser(username);
                log.warn("检测到超时用户，已移除: username={}, name={}, 最后心跳时间={}",
                        username, name, new java.util.Date(lastHeartbeat));

                notifyUserOffline(name);
            }
        }
    }

    private void notifyUserOffline(String name) {
        if (name != null) {
            try {
                Map<String, Object> offlineNotification = new HashMap<>();
                offlineNotification.put("type", "user_offline");
                offlineNotification.put("sender", name);
                offlineNotification.put("message", name + " 已下线");
                offlineNotification.put("sendTime", java.time.LocalDateTime.now().toString());

                messagingTemplate.convertAndSend("/topic/private-notifications", (Object) offlineNotification);
            } catch (Exception e) {
                log.error("发送离线通知失败", e);
            }
        }
    }
}

