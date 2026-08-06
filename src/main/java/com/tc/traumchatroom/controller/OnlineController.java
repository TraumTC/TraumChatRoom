package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.entity.OnlineUserInfo;
import com.tc.traumchatroom.service.OnlineUserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 在线用户控制器
 * GET /api/online/users
 */
@RestController
@RequestMapping("/api/online")
public class OnlineController {

    @Resource
    private OnlineUserService onlineUserService;

    /**
     * 获取在线用户列表
     * GET /api/online/users
     */
    @GetMapping("/users")
    public Result<OnlineUserInfo> getOnlineUsers() {
        Set<String> users = onlineUserService.getOnlineUsers();
        OnlineUserInfo info = new OnlineUserInfo();
        info.setOnlineUsers(users);
        info.setCount(users != null ? users.size() : 0);
        return Result.success(info);
    }
}
