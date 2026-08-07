package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * Spring Security 用户详情服务
 * 作用：根据用户名加载用户信息，供 Security 进行认证
 *
 * 支持两种用户：
 * 1. 普通用户：从数据库查询
 * 2. 游客用户（guest_ 开头）：从 Redis 查询（游客不写数据库）
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 根据用户名加载用户详情
     * @param username 用户名
     * @return UserDetails（Spring Security 的用户对象）
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 游客用户：从 Redis 读取（不存数据库）
        if (username.startsWith("guest_")) {
            String guestKey = "chat:guest:" + username;
            Map<Object, Object> guestData = redisTemplate.opsForHash().entries(guestKey);
            if (guestData.isEmpty()) {
                throw new UsernameNotFoundException("游客不存在或已过期: " + username);
            }
            return new org.springframework.security.core.userdetails.User(
                    username,
                    "",  // 游客无密码
                    Collections.singletonList(new SimpleGrantedAuthority((String) guestData.get("role")))
            );
        }

        // 普通用户：从数据库查询
        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 返回 Spring Security 的 UserDetails 对象
        // 参数：用户名、密码、权限列表
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );
    }
}
