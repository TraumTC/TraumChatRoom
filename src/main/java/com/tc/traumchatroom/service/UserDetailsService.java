package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.MyUserDetails;
import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    @Resource
    private UserMapper userMapper;

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = this.userMapper.findByUsername(username);
        if (user == null){
            throw new UsernameNotFoundException("用户名"+ username +"不存在");
        }

        // 返回自己封装的UserDetails
        return new MyUserDetails(user);
    }
}
