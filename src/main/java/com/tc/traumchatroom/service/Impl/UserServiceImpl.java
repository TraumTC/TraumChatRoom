package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import jdk.jfr.Registered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;
    @Override
    public void register(String username, String name, String password) throws Exception {
        User user = new User();
        user = this.userMapper.findByUsername(username);
        if (user != null){
            throw new Exception("用户"+ username +"已存在");
        }else if (this.userMapper.findByName(name) != null){
            throw new Exception("昵称"+ name +"已存在");
        }else {
            user.setUsername(username);
            user.setName(name);
            user.setPassword(this.passwordEncoder.encode(password));
            user.setRole("ROLE_USER");
            this.userMapper.addUser(user);
        }
    }


}
