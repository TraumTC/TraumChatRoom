package com.tc.traumchatroom.util;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.mapper.UserMapper;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

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
    public User getCurrentUser( SimpMessageHeaderAccessor headerAccessor){
        User currentUser = null;
        String authenticatedUser = (String) headerAccessor.getSessionAttributes().get("authenticatedUser");
        currentUser = userMapper.findByUsername(authenticatedUser);
        currentUser.setPassword(null);
        return currentUser;
    }



}
