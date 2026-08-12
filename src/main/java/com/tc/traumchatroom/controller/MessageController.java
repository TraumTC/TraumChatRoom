package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.dto.request.MarkReadRequest;
import com.tc.traumchatroom.dto.response.MessageResponse;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.vo.CursorPageVO;
import com.tc.traumchatroom.dto.vo.UnreadSummaryVO;
import com.tc.traumchatroom.service.ChatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器
 * 路径前缀：/api/messages
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Resource
    private ChatService chatService;

    /**
     * 群聊历史消息（游标分页）
     * GET /api/messages/history?cursor=1234&size=20
     */
    @GetMapping("/history")
    public Result<CursorPageVO<MessageResponse>> getGroupHistory(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        CursorPageVO<MessageResponse> result = chatService.getGroupHistory(cursor, size);
        return Result.success(result);
    }

    /**
     * 私聊历史消息（游标分页）
     * GET /api/messages/private/{targetUsername}?cursor=5678&size=20
     */
    @GetMapping("/private/{targetUsername}")
    public Result<CursorPageVO<MessageResponse>> getPrivateHistory(
            @PathVariable String targetUsername,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        String currentUsername = getCurrentUsername();
        CursorPageVO<MessageResponse> result = chatService.getPrivateHistory(currentUsername, targetUsername, cursor, size);
        return Result.success(result);
    }

    /**
     * 撤回消息
     * PUT /api/messages/{id}/recall
     */
    @LogOperation(action = "RECALL_MESSAGE", targetType = "message")
    @PutMapping("/{id}/recall")
    public Result<Void> recallMessage(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        chatService.recallMessage(id, username, role);
        return Result.success();
    }

    /**
     * 私聊未读汇总（离线/未打开会话消息，按发送者分组）
     * GET /api/messages/unread-summary
     */
    @GetMapping("/unread-summary")
    public Result<List<UnreadSummaryVO>> getUnreadSummary() {
        String username = getCurrentUsername();
        return Result.success(chatService.getUnreadSummary(username));
    }

    /**
     * 标记某私聊会话已读（推进 Redis 已读游标）
     * POST /api/messages/read  body: { "targetUsername": "xxx" }
     */
    @PostMapping("/read")
    public Result<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
        String username = getCurrentUsername();
        chatService.markConversationRead(username, request.getTargetUsername());
        return Result.success();
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
