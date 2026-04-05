package com.tc.traumchatroom.service;

import com.tc.traumchatroom.entity.User;

public interface UserService {
    void register(String Username,String name,String password) throws Exception;
    User getCurrentUser();
    User getOrCreateGuestUser(jakarta.servlet.http.HttpServletRequest request);
}
