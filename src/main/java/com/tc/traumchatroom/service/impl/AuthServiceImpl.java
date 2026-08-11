package com.tc.traumchatroom.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.traumchatroom.dto.request.LoginRequest;
import com.tc.traumchatroom.dto.request.RefreshRequest;
import com.tc.traumchatroom.dto.request.RegisterRequest;
import com.tc.traumchatroom.dto.response.LoginResponse;
import com.tc.traumchatroom.dto.response.UserResponse;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.AuthService;
import com.tc.traumchatroom.service.CacheService;
import com.tc.traumchatroom.util.GuestNameUtil;
import com.tc.traumchatroom.util.JwtUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private CacheService cacheService;

    // ---------- 注册 ----------

    @Override
    public UserResponse register(RegisterRequest request) {
        // 0. 禁止占用 AI 用户保留名称（用户名 ai_xiaoai / 昵称 小爱）
        if ("ai_xiaoai".equalsIgnoreCase(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }
        if ("小爱".equals(request.getName())) {
            throw new BusinessException(ErrorCode.NAME_EXISTS);
        }

        // 1. 检查用户名是否已存在
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // 2. 检查昵称是否已存在
        if (userMapper.findByName(request.getName()) != null) {
            throw new BusinessException(ErrorCode.NAME_EXISTS);
        }

        // 3. 创建用户（密码 BCrypt 加密）
        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("ROLE_USER");
        user.setStatus(1);

        userMapper.insert(user);

        log.info("新用户注册: {}", request.getUsername());
        return UserResponse.fromEntity(user);
    }

    // ---------- 登录（含频率限制） ----------

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 1. 检查是否被锁定（用户名维度 + IP 维度）
        String userLockKey = "chat:login:lock:user:" + request.getUsername();
        String ipLockKey = "chat:login:lock:ip:" + clientIp;

        checkLock(userLockKey, "登录失败次数过多，账号已锁定");
        checkLock(ipLockKey, "登录失败次数过多，IP已锁定");

        // 2. 根据用户名查询用户
        User user = userMapper.findByUsername(request.getUsername());

        // 3. 验证密码（用户不存在也走同样的错误提示，防止枚举用户名）
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 密码错误，记录失败次数（用户名 5 次 / IP 10 次）
            incrementFailCount("chat:login:fail:user:" + request.getUsername(), 5, 15, TimeUnit.MINUTES);
            incrementFailCount("chat:login:fail:ip:" + clientIp, 10, 30, TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "用户名或密码错误");
        }

        // 4. 检查账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 登录成功，清除失败计数（用户维度 + IP 维度）
        redisTemplate.delete("chat:login:fail:user:" + request.getUsername());
        redisTemplate.delete("chat:login:fail:ip:" + clientIp);

        // 6. 更新最后活跃时间
        userMapper.updateLastActiveTime(user.getId());

        // 7. 写入用户信息缓存（Cache-Aside）
        cacheService.cacheUser(user);

        log.info("用户登录成功: {}", request.getUsername());
        return generateLoginResponse(user);
    }

    // ---------- 刷新 Token ----------

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();

        // 1. 验证 refreshToken 是否有效
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 已过期，请重新登录");
        }

        // 2. 检查 Redis 中是否存在（登出后会被删除）
        String username = jwtUtil.getUsernameFromToken(token);
        String redisKey = "chat:refresh:" + username;
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        if (storedToken == null || !storedToken.equals(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 无效，请重新登录");
        }

        // 3. 查询用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 4. 生成新 Token
        return generateLoginResponse(user);
    }

    // ---------- 登出 ----------

    @Override
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            try {
                // 从 token 中解析用户名，删除 Redis 中的记录
                String username = jwtUtil.getUsernameFromToken(refreshToken);
                redisTemplate.delete("chat:refresh:" + username);
                log.info("用户登出: {}", username);
            } catch (Exception e) {
                // token 无效时忽略，登出本身是幂等的
                log.warn("登出时解析 token 失败: {}", e.getMessage());
            }
        }
    }

    // ---------- 获取当前用户（支持游客从 Redis 读取） ----------

    @Override
    public UserResponse getCurrentUser(String username) {
        // 1. 先查数据库
        User user = userMapper.findByUsername(username);

        // 2. 如果数据库没有，检查是否是游客（Redis）
        if (user == null && username.startsWith("guest_")) {
            String guestKey = "chat:guest:" + username;
            Map<Object, Object> guestData = redisTemplate.opsForHash().entries(guestKey);
            if (!guestData.isEmpty()) {
                user = new User();
                user.setUsername((String) guestData.get("username"));
                user.setName((String) guestData.get("name"));
                user.setRole((String) guestData.get("role"));
                user.setStatus(1);
                return UserResponse.fromEntity(user);
            }
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return UserResponse.fromEntity(user);
    }

    // ---------- 游客登录（不写入数据库，只存 Redis） ----------

    @Override
    public LoginResponse loginAsGuest(String userAgent, String clientIp) {
        // 1. 生成唯一游客名
        String username = GuestNameUtil.generateGuestUsername();
        String name = GuestNameUtil.generateGuestName(userAgent, clientIp);

        // 2. 游客信息存入 Redis（不写数据库）
        String guestKey = "chat:guest:" + username;
        Map<String, String> guestInfo = Map.of(
                "username", username,
                "name", name,
                "role", "ROLE_GUEST",
                "ip", clientIp != null ? clientIp : "unknown"
        );
        redisTemplate.opsForHash().putAll(guestKey, guestInfo);
        redisTemplate.expire(guestKey, 2, TimeUnit.HOURS);  // 2小时过期

        // 3. 生成 Token（游客专用，不查数据库）
        String accessToken = jwtUtil.generateAccessToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);

        // 4. 构造游客用户对象（不存数据库）
        User guest = new User();
        guest.setUsername(username);
        guest.setName(name);
        guest.setRole("ROLE_GUEST");
        guest.setStatus(1);

        // 5. refreshToken 也存 Redis
        redisTemplate.opsForValue().set(
                "chat:refresh:" + username,
                refreshToken,
                2, TimeUnit.HOURS
        );

        log.info("游客登录: {} (仅存Redis)", name);

        return new LoginResponse(accessToken, refreshToken, 7200L, UserResponse.fromEntity(guest));
    }

    // ---------- 私有方法 ----------

    /**
     * 生成登录响应（accessToken + refreshToken + 用户信息）
     */
    private LoginResponse generateLoginResponse(User user) {
        // 1. 生成 Token
        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        // 2. refreshToken 存入 Redis（支持登出时主动失效）
        redisTemplate.opsForValue().set(
                "chat:refresh:" + user.getUsername(),
                refreshToken,
                7, TimeUnit.DAYS
        );

        return new LoginResponse(accessToken, refreshToken, 86400L, UserResponse.fromEntity(user));
    }

    /**
     * 检查是否被锁定
     */
    private void checkLock(String lockKey, String errorMsg) {
        String locked = redisTemplate.opsForValue().get(lockKey);
        if (locked != null) {
            Long remain = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    errorMsg + "，请在 " + formatTime(remain) + " 后重试");
        }
    }

    /**
     * 记录失败次数，达到上限后锁定
     */
    private void incrementFailCount(String failKey, int maxCount, long timeout, TimeUnit unit) {
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 第一次失败，设置过期时间
            redisTemplate.expire(failKey, timeout, unit);
        }
        if (count != null && count >= maxCount) {
            // 达到上限，锁定（fail → lock）
            String lockKey = failKey.replace("fail", "lock");
            redisTemplate.opsForValue().set(lockKey, "1", timeout, unit);
            // 清除失败计数
            redisTemplate.delete(failKey);
        }
    }

    /**
     * 将秒数格式化为 "x 分 y 秒"
     */
    private String formatTime(Long seconds) {
        if (seconds == null || seconds <= 0) return "1 分钟";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes > 0 ? minutes + " 分 " + secs + " 秒" : secs + " 秒";
    }
}
