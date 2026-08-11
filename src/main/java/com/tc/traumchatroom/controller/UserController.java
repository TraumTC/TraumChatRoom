package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.dto.request.UpdatePasswordRequest;
import com.tc.traumchatroom.dto.request.UpdateProfileRequest;
import com.tc.traumchatroom.dto.response.MentionableUserResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 * 路径前缀：/api/user
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 修改个人资料
     * PUT /api/user/profile
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        String username = getCurrentUsername();
        userService.updateProfile(username, request);
        return Result.success();
    }

    /**
     * 修改密码
     * PUT /api/user/password
     */
    @LogOperation(action = "CHANGE_PASSWORD", targetType = "user")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        String username = getCurrentUsername();
        userService.updatePassword(username, request);
        return Result.success();
    }

    /**
     * 上传头像
     * POST /api/user/avatar
     */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String username = getCurrentUsername();
        String avatarUrl = userService.uploadAvatar(username, file);
        return Result.success(Map.of("avatarUrl", avatarUrl));
    }

    /**
     * 删除头像
     * DELETE /api/user/avatar
     */
    @DeleteMapping("/avatar")
    public Result<Void> deleteAvatar() {
        String username = getCurrentUsername();
        userService.deleteAvatar(username);
        return Result.success();
    }

    /**
     * 获取可@用户列表
     * GET /api/user/mentionable
     */
    @GetMapping("/mentionable")
    public Result<List<MentionableUserResponse>> getMentionableUsers() {
        String username = getCurrentUsername();
        return Result.success(userService.getMentionableUsers(username));
    }

    /**
     * 从 SecurityContext 获取当前登录用户名
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
