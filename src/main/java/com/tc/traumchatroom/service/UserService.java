package com.tc.traumchatroom.service;

import com.tc.traumchatroom.dto.request.UpdatePasswordRequest;
import com.tc.traumchatroom.dto.request.UpdateProfileRequest;
import com.tc.traumchatroom.dto.response.MentionableUserResponse;
import com.tc.traumchatroom.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 修改个人资料（昵称）
     */
    void updateProfile(String username, UpdateProfileRequest request);

    /**
     * 修改密码
     */
    void updatePassword(String username, UpdatePasswordRequest request);

    /**
     * 上传头像
     * @return 头像访问URL
     */
    String uploadAvatar(String username, MultipartFile file);

    /**
     * 删除头像（恢复默认）
     */
    void deleteAvatar(String username);

    /**
     * 获取可@用户列表（排除自己，包含AI用户）
     */
    List<MentionableUserResponse> getMentionableUsers(String currentUsername);
}
