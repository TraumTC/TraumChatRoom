package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security 用户详情服务
 * 作用：根据用户名从数据库加载用户信息，供 Security 进行认证
 *
 * 为什么需要这个？
 * Spring Security 不直接操作数据库，而是通过 UserDetailsService 接口获取用户
 * 这样 Security 和数据库之间就解耦了
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private UserMapper userMapper;

    /**
     * 根据用户名加载用户详情
     * @param username 用户名
     * @return UserDetails（Spring Security 的用户对象）
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查询用户
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
