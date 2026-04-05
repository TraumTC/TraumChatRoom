package com.tc.traumchatroom.service.Impl;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.UserService;
import com.tc.traumchatroom.util.GuestNameUtil;
import com.tc.traumchatroom.util.UserUtil;
import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserUtil userUtil;

    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private GuestNameUtil guestNameUtil;
    @Override
    public void register(String username, String name, String password) throws Exception {
        User user = this.userMapper.findByUsername(username);
        if (user != null){
            throw new Exception("用户:"+ username +" 已存在!");
        }else if (this.userMapper.findByName(name) != null){
            throw new Exception("昵称:"+ name +" 已存在!");
        }else {
            user = new User();
            user.setUsername(username);
            user.setName(name);
            user.setPassword(this.passwordEncoder.encode(password));
            user.setRole("ROLE_USER");
            this.userMapper.addUser(user);
        }
    }

    @Override
    public User getCurrentUser() {
        User user = userUtil.getCurrentUser();
        if (user == null){
            return null;
        }
        return user;
    }

    @Override
    public User getOrCreateGuestUser(HttpServletRequest request) {
        String guestName = guestNameUtil.generateGuestName(request);

        User guestUser = userMapper.findByName(guestName);

        if (guestUser == null) {
            guestUser = new User();
            guestUser.setUsername("guest_" + System.currentTimeMillis());
            guestUser.setName(guestName);
            guestUser.setPassword("");
            guestUser.setRole("ROLE_GUEST");
            userMapper.addUser(guestUser);

            guestUser = userMapper.findByName(guestName);
        }

        if (guestUser != null) {
            guestUser.setPassword(null);
        }

        return guestUser;
    }

}
