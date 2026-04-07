package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {
    @Resource
    private UserMapper userMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = this.userMapper.findByUsername(username);
        if (user == null){
            throw new UsernameNotFoundException("用户名"+ username +"不存在");
        }
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole()) ;
        authorities.add(authority);
        return new org.springframework.security.core.userdetails.User(username,user.getPassword(),authorities);
    }
}
