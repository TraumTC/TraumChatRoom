package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.annotation.LogOperation;
import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.dto.response.UserResponse;
import com.tc.traumchatroom.entity.OperationLog;
import com.tc.traumchatroom.entity.SensitiveWord;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.MessageMapper;
import com.tc.traumchatroom.mapper.OperationLogMapper;
import com.tc.traumchatroom.mapper.SensitiveWordMapper;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.SensitiveWordFilter;
import com.tc.traumchatroom.service.CacheService;
import com.tc.traumchatroom.service.RefreshTokenStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员控制器
 * 路径前缀：/api/admin
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private CacheService cacheService;

    @Resource
    private RefreshTokenStore refreshTokenStore;

    @Resource
    private MessageMapper messageMapper;

    /**
     * 允许的角色白名单。
     *
     * 原来 role 只校验非空就直接落库，任意字符串都收：
     * - 拼错成 ROLE_USRE → 该用户所有 hasRole 判断静默失效，权限行为异常且极难排查；
     * - 填 ROLE_AI → 命中 assertNotProtected 的 isAiUser 判定，
     *   把一个普通账号变成不可删、不可改的「受保护用户」，管理端再也处理不了它。
     * ROLE_GUEST 不在白名单内：游客只存在于 Redis，从不落 user 表，
     * 把库里的账号改成游客角色没有任何合法用途。
     */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");

    /**
     * 校验角色取值合法，非法直接拒绝。
     */
    private void assertAssignableRole(String role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "角色只能是 ROLE_USER 或 ROLE_ADMIN，收到: " + role);
        }
    }

    /**
     * 获取敏感词列表（分页）
     * GET /api/admin/sensitive-words?page=1&size=20&level=1&category=insult
     */
    @GetMapping("/sensitive-words")
    public Result<Map<String, Object>> getSensitiveWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String category) {

        int offset = (page - 1) * size;
        List<SensitiveWord> words = sensitiveWordMapper.findByConditions(level, category, offset, size);
        int total = sensitiveWordMapper.countByConditions(level, category);

        Map<String, Object> data = new HashMap<>();
        data.put("items", words);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("totalPages", (total + size - 1) / size);

        return Result.success(data);
    }

    /**
     * 添加敏感词
     * POST /api/admin/sensitive-words
     */
    @LogOperation(action = "ADD_SENSITIVE_WORD", targetType = "sensitive_word")
    @PostMapping("/sensitive-words")
    public Result<Map<String, Object>> addSensitiveWord(@RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        Integer level = (Integer) body.get("level");
        String category = (String) body.get("category");

        if (word == null || word.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }

        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word.trim());
        sw.setLevel(level != null ? level : 1);
        sw.setCategory(category);

        sensitiveWordMapper.insert(sw);

        // 刷新内存中的敏感词库
        sensitiveWordFilter.refresh();

        log.info("添加敏感词: {}", word);

        Map<String, Object> data = new HashMap<>();
        data.put("id", sw.getId());
        data.put("word", sw.getWord());
        data.put("level", sw.getLevel());
        data.put("category", sw.getCategory());

        return Result.success(data);
    }

    /**
     * 修改敏感词
     * PUT /api/admin/sensitive-words/{id}
     */
    @LogOperation(action = "UPDATE_SENSITIVE_WORD", targetType = "sensitive_word")
    @PutMapping("/sensitive-words/{id}")
    public Result<Void> updateSensitiveWord(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        SensitiveWord existing = sensitiveWordMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }

        String word = (String) body.get("word");
        Integer level = (Integer) body.get("level");
        String category = (String) body.get("category");

        if (word == null || word.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }

        existing.setWord(word.trim());
        existing.setLevel(level != null ? level : existing.getLevel());
        existing.setCategory(category);

        sensitiveWordMapper.update(existing);

        // 刷新内存中的敏感词库
        sensitiveWordFilter.refresh();

        log.info("修改敏感词: {} -> {}", id, existing.getWord());

        return Result.success();
    }

    /**
     * 删除敏感词
     * DELETE /api/admin/sensitive-words/{id}
     */
    @LogOperation(action = "DELETE_SENSITIVE_WORD", targetType = "sensitive_word")
    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteSensitiveWord(@PathVariable Integer id) {
        SensitiveWord word = sensitiveWordMapper.findById(id);
        if (word == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }

        sensitiveWordMapper.deleteById(id);

        // 刷新内存中的敏感词库
        sensitiveWordFilter.refresh();

        log.info("删除敏感词: {}", word.getWord());

        return Result.success();
    }

    /**
     * 刷新敏感词库
     * POST /api/admin/sensitive-words/refresh
     */
    @PostMapping("/sensitive-words/refresh")
    public Result<Map<String, Object>> refreshSensitiveWords() {
        sensitiveWordFilter.refresh();

        Map<String, Object> data = new HashMap<>();
        data.put("count", sensitiveWordFilter.getWordCount());
        data.put("message", "敏感词库已刷新");

        return Result.success(data);
    }

    /**
     * 查询操作日志（分页）
     * GET /api/admin/logs?action=LOGIN&targetType=user&username=张三&success=true&page=1&size=20&startDate=2026-07-01&endDate=2026-07-21
     */
    @GetMapping("/logs")
    public Result<Map<String, Object>> getLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        int offset = (page - 1) * size;
        List<OperationLog> logs = operationLogMapper.findByConditions(action, targetType, username, success, startDate, endDate, offset, size);
        int total = operationLogMapper.countByConditions(action, targetType, username, success, startDate, endDate);

        Map<String, Object> data = new HashMap<>();
        data.put("items", logs);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("totalPages", (total + size - 1) / size);

        return Result.success(data);
    }

    /**
     * 获取用户列表（管理员分页）
     * GET /api/admin/users?page=1&size=20&keyword=张&includeDeleted=false
     */
    @GetMapping("/users")
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        int offset = (page - 1) * size;
        List<User> users = userMapper.findAll(keyword, includeDeleted, offset, size);
        int total = userMapper.countAll(keyword, includeDeleted);

        // 转换为响应 DTO（去掉密码字段）
        List<UserResponse> items = users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("totalPages", (total + size - 1) / size);

        return Result.success(data);
    }

    /**
     * 修改用户角色
     * PUT /api/admin/users/{id}/role
     */
    @LogOperation(action = "CHANGE_ROLE", targetType = "user")
    @PutMapping("/users/{id}/role")
    @Transactional
    public Result<Void> updateRole(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不能为空");
        }
        assertAssignableRole(role);

        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        assertNotProtected(user, "修改角色");

        userMapper.updateRole(id, role);
        cacheService.evictUserAfterCommit(id);
        log.info("管理员修改用户 {} 角色为 {}", id, role);

        return Result.success();
    }

    /**
     * 修改用户信息
     * PUT /api/admin/users/{id}
     */
    @LogOperation(action = "CHANGE_PROFILE", targetType = "user")
    @PutMapping("/users/{id}")
    @Transactional
    public Result<Void> updateUser(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        String name = (String) body.get("name");
        String role = (String) body.get("role");
        Integer status = (Integer) body.get("status");
        String password = (String) body.get("password");

        // 保护校验（前置）：小汤与管理员禁止改角色/禁用/重置密码；小汤昵称固定，管理员昵称可改
        if (role != null && !role.isBlank()) {
            assertAssignableRole(role);
            assertNotProtected(user, "修改角色");
        }
        if (status != null) {
            assertNotProtected(user, "禁用/启用");
        }
        if (password != null && !password.isBlank()) {
            assertNotProtected(user, "重置密码");
        }
        if (name != null && !name.isBlank() && !name.equals(user.getName())) {
            if (isAiUser(user)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "小汤为系统AI助手，昵称固定，不能修改");
            }
        }

        // 修改昵称（含已软删除用户查重，避免撞唯一键抛 500）
        String renamedFrom = null;
        if (name != null && !name.isBlank() && !name.equals(user.getName())) {
            User existing = userMapper.findByNameIncludingDeleted(name);
            if (existing != null) {
                throw new BusinessException(ErrorCode.NAME_EXISTS);
            }
            renamedFrom = user.getName();
            user.setName(name);
        }

        // 修改角色（updateProfile 的 SQL 不含 role 列，必须单独走 updateRole 落库）
        if (role != null && !role.isBlank()) {
            userMapper.updateRole(id, role);
        }

        // 修改状态（updateProfile 的 SQL 不含 status 列，必须单独走 updateStatus 落库）
        if (status != null) {
            userMapper.updateStatus(id, status);
        }

        // 重置密码（与注册规则一致：6-20位，含字母和数字）
        if (password != null && !password.isBlank()) {
            if (password.length() < 6 || password.length() > 20) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需6-20位");
            }
            if (!password.matches(".*[a-zA-Z].*") || !password.matches(".*[0-9].*")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "密码必须同时包含字母和数字");
            }
            userMapper.updatePassword(id, passwordEncoder.encode(password));
        }

        userMapper.updateProfile(user);
        // 同步历史消息里的冗余 sender_name。
        // 这一步原先是数据库触发器 trg_user_name_update 做的，而该触发器同时会把
        // message.receiver_name（存的是 username）按昵称匹配改写，污染私聊会话定位（报告 P1-9），
        // 已随 V3__drop_user_name_trigger.sql 删除。管理员改名这条路径此前完全依赖触发器，
        // 因此必须在这里补上 —— 与用户自己改名走同一个按 sender_id 精确定位的 updateSenderName。
        if (renamedFrom != null) {
            int updated = messageMapper.updateSenderName(id, renamedFrom, user.getName());
            log.info("管理员改用户 {} 昵称: {} -> {}，同步更新 {} 条消息的 sender_name",
                    id, renamedFrom, user.getName(), updated);
        }
        cacheService.evictUserAfterCommit(id);
        if ((status != null && status == 0) || (password != null && !password.isBlank())) {
            refreshTokenStore.revokeAll(user.getUsername());
        }
        log.info("管理员修改用户 {} 信息", id);

        return Result.success();
    }

    /**
     * 删除用户（软删除）
     * DELETE /api/admin/users/{id}
     */
    @LogOperation(action = "DELETE_USER", targetType = "user")
    @DeleteMapping("/users/{id}")
    @Transactional
    public Result<Void> deleteUser(@PathVariable Integer id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 小汤 / 管理员受保护，不能删除
        assertNotProtected(user, "删除");

        // 不能删除自己
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(currentUsername)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能删除自己");
        }

        userMapper.softDelete(id);
        cacheService.evictUserAfterCommit(id);
        refreshTokenStore.revokeAll(user.getUsername());
        log.info("管理员删除用户: {}", user.getUsername());

        return Result.success();
    }

    /** 是否系统 AI 助手（小汤） */
    private boolean isAiUser(User user) {
        return "ai_xiaoai".equals(user.getUsername()) || "ROLE_AI".equals(user.getRole());
    }

    /**
     * 受保护用户校验：小汤与管理员不允许被敏感操作（改角色/禁用/删除/重置密码）。
     * 昵称修改除外（管理员可改自己的昵称，小汤昵称固定由调用方单独判断）。
     */
    private void assertNotProtected(User user, String action) {
        if (isAiUser(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "小汤为系统AI助手，不能" + action);
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能对管理员" + action);
        }
    }
}
