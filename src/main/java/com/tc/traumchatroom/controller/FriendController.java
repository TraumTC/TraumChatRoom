package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.Idempotent;
import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.dto.request.FriendApplyRequest;
import com.tc.traumchatroom.dto.request.FriendHandleRequest;
import com.tc.traumchatroom.dto.response.FriendResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.FriendRequestPageVO;
import com.tc.traumchatroom.dto.vo.FriendSearchVO;
import com.tc.traumchatroom.service.FriendService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友控制器
 * 路径前缀：/api/friend 和 /api/friends
 */
@Slf4j
@RestController
public class FriendController {

    @Resource
    private FriendService friendService;

    /**
     * 搜索用户
     * GET /api/friend/search?keyword=张
     */
    @GetMapping("/api/friend/search")
    public Result<List<FriendSearchVO>> search(@RequestParam String keyword) {
        String username = getCurrentUsername();
        return Result.success(friendService.searchUsers(keyword, username));
    }

    /**
     * 发送好友申请
     * POST /api/friend/request
     * 幂等：防重复点击导致重复申请（配合 X-Request-Id header）
     */
    @Idempotent(key = "friend-apply", timeout = 5)
    @LogOperation(action = "ADD_FRIEND", targetType = "friend")
    @PostMapping("/api/friend/request")
    public Result<Void> apply(@Valid @RequestBody FriendApplyRequest request) {
        String username = getCurrentUsername();
        friendService.sendRequest(username, request);
        return Result.success();
    }

    /**
     * 获取好友申请列表
     * GET /api/friend/requests?type=received&status=pending&page=1&size=20
     */
    @GetMapping("/api/friend/requests")
    public Result<FriendRequestPageVO> getRequests(
            @RequestParam(defaultValue = "received") String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = getCurrentUsername();
        return Result.success(friendService.getRequests(username, type, status, page, size));
    }

    /**
     * 处理好友申请
     * PUT /api/friend/requests/{id}
     */
    @PutMapping("/api/friend/requests/{id}")
    public Result<Void> handleRequest(@PathVariable Long id,
                                       @Valid @RequestBody FriendHandleRequest request) {
        String username = getCurrentUsername();
        friendService.handleRequest(id, username, request);
        return Result.success();
    }

    /**
     * 获取好友列表
     * GET /api/friends?page=1&size=20&keyword=李
     */
    @GetMapping("/api/friends")
    public Result<CursorPageVO<FriendResponse>> getFriends(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = getCurrentUsername();
        return Result.success(friendService.getFriends(username, keyword, page, size));
    }

    /**
     * 修改好友备注
     * PUT /api/friends/{friendId}/remark
     */
    @PutMapping("/api/friends/{friendId}/remark")
    public Result<Void> updateRemark(@PathVariable Integer friendId,
                                      @RequestBody Map<String, String> body) {
        String username = getCurrentUsername();
        friendService.updateRemark(username, friendId, body.get("remark"));
        return Result.success();
    }

    /**
     * 删除好友申请记录
     * DELETE /api/friend/requests/{id}
     */
    @DeleteMapping("/api/friend/requests/{id}")
    public Result<Void> deleteRequest(@PathVariable Long id) {
        String username = getCurrentUsername();
        friendService.deleteRequest(id, username);
        return Result.success();
    }

    /**
     * 删除好友
     * DELETE /api/friends/{friendId}
     */
    @LogOperation(action = "DELETE_FRIEND", targetType = "friend")
    @DeleteMapping("/api/friends/{friendId}")
    public Result<Void> deleteFriend(@PathVariable Integer friendId) {
        String username = getCurrentUsername();
        friendService.deleteFriend(username, friendId);
        return Result.success();
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
