package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;

public interface UserService {
    public User getCurrentUser();
    public User getCurrentUser(SimpMessageHeaderAccessor headerAccessor);
    public User getCurrentUserWithGuest(HttpServletRequest request);
    public void register(String username, String name, String password) throws Exception;
    public User getOrCreateGuestUser(HttpServletRequest request);
    public int updateProfile(Integer id, String name, String password);
    public List<User> getAllUsers();
    public int updateUserRole(Integer id, String role);
    public int deleteUser(Integer id);
    public User findByName(String name);
    public User findByUserName(String username);
    public int updateUser(User user);



}
