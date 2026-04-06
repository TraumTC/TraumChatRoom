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

import java.util.List;

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
    public int updateProfile(Integer id, String name, String password) {
        User user = new User();
        user.setId(id);
        if (name != null && !name.trim().isEmpty()) {
            user.setName(name);
        }
        if (password != null && !password.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        return userMapper.updateNameAndPassword(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userMapper.findAll();
    }

    @Override
    public int updateUserRole(Integer id, String role) {
        return userMapper.updateRole(id, role);
    }

    @Override
    public int deleteUser(Integer id) {
        return userMapper.deleteUser(id);
    }

    @Override
    public User findByName(String name) {
        return userMapper.findByName(name);
    }

    @Override
    public User findByUserName(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public int updateUser(User user) {
        return userMapper.updateUser(user);
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
