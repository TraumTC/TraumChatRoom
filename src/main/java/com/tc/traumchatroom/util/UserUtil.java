package com.tc.traumchatroom.util;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;


@Component
public class UserUtil {
    @Resource
    private UserMapper userMapper;


    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) auth.getPrincipal()).getUsername();
            User user = userMapper.findByUsername(username);
            if (user != null) {
                user.setPassword(null);
                return user;
            }
        }
        return null;
    }

    public User getCurrentUser(SimpMessageHeaderAccessor headerAccessor){
        String authenticatedUser = (String) headerAccessor.getSessionAttributes().get("authenticatedUser");
        String authenticatedName = (String) headerAccessor.getSessionAttributes().get("authenticatedUserName");

        if (authenticatedUser == null || authenticatedUser.isEmpty()) {
            return null;
        }

        if (authenticatedUser.startsWith("guest_")) {
            User guestUser = new User();
            guestUser.setUsername(authenticatedUser);
            guestUser.setName(authenticatedName);
            guestUser.setRole("ROLE_GUEST");
            return guestUser;
        }

        User currentUser = userMapper.findByUsername(authenticatedUser);
        if (currentUser != null) {
            currentUser.setPassword(null);
        }
        return currentUser;
    }



}
