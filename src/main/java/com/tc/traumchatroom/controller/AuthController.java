package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.Impl.UserDetailsServiceImpl;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller

public class AuthController {

    @Resource
    private UserService userService;
    @Resource
    private UserDetailsServiceImpl userDetailsService;
    @GetMapping("/register")
    public String register() {
        return "register";
    }
    @PostMapping("/register")
    public String registerSubmit(@RequestParam String username,
                                 @RequestParam String name,
                                 @RequestParam String password,RedirectAttributes redirectAttributes)  {
        try {
            userService.register(username,name,password);
            redirectAttributes.addFlashAttribute("successMessage","注册成功");
            return "redirect:/login?success=registered";
        }catch (Exception e){
            redirectAttributes.addFlashAttribute("errorMessage",e.getMessage());
            return "redirect:/register";
        }

    }
    @GetMapping("/login")
    public String login() {
        if (userService.getCurrentUser() != null) {
            return "redirect:/ChatRoom";
        }
        return "login";
    }
    @GetMapping("/ChatRoom/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/ChatRoom")
    public String ChatRoom() {
            return "ChatRoom";
        }
    @GetMapping("/api/current-user")
    @ResponseBody
    public User getCurrentUser(HttpServletRequest request) {
        return userService.getCurrentUser();
    }
}
