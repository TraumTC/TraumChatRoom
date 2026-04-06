package com.tc.traumchatroom.controller;

import com.tc.traumchatroom.entity.User;
import com.tc.traumchatroom.service.Impl.UserDetailsServiceImpl;
import com.tc.traumchatroom.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller

public class AuthController {
    private Map<String, Object> result = new HashMap<>();

    @Resource
    private UserService userService;
    @Resource
    private PasswordEncoder passwordEncoder;
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
    public String ChatRoom(HttpServletRequest request, HttpSession session) {
        User currentUser = userService.getCurrentUser();

        if (currentUser == null) {
            User guestUser = userService.getOrCreateGuestUser(request);
            if (guestUser != null) {
                session.setAttribute("GUEST_USER", guestUser);
            }
        }
        return "ChatRoom";
    }
    @GetMapping("/api/current-user")
    @ResponseBody
    public User getCurrentUser(HttpServletRequest request, HttpSession session) {
        User currentUser = userService.getCurrentUser();

        if (currentUser == null) {
            User guestUser = (User) session.getAttribute("GUEST_USER");
            if (guestUser == null) {
                guestUser = userService.getOrCreateGuestUser(request);
                if (guestUser != null) {
                    session.setAttribute("GUEST_USER", guestUser);
                }
            }
            return guestUser;
        }
        return currentUser;
    }
    @GetMapping("/profile")
    public String profile() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        return "profile";
    }

    @PostMapping("/api/update-profile")
    @ResponseBody
    public Map<String, Object> updateProfile(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) String password) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = userService.getCurrentUser();

        if (currentUser == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }

        try {
            if (name != null && !name.trim().isEmpty()) {
                User existUser = userService.findByName(name);
                if (existUser != null && !existUser.getId().equals(currentUser.getId())) {
                    result.put("success", false);
                    result.put("message", "昵称已存在");
                    return result;
                }
            }

            int rows = userService.updateProfile(currentUser.getId(), name, password);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "修改成功");
            } else {
                result.put("success", false);
                result.put("message", "修改失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "修改失败：" + e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/update-password")
    @ResponseBody
    public Map<String, Object> updatePassword(@RequestParam String oldPassword,
                                              @RequestParam String password) {

        User currentUser = userService.getCurrentUser();

        if (currentUser == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }

        try {
            User dbUser = userService.findByUserName(currentUser.getUsername());
            if (dbUser == null || !passwordEncoder.matches(oldPassword, dbUser.getPassword())) {
                result.put("success", false);
                result.put("message", "当前密码错误");
                return result;
            }

            int rows = userService.updateProfile(currentUser.getId(), null, password);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "密码修改成功");
            } else {
                result.put("success", false);
                result.put("message", "修改失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "修改失败：" + e.getMessage());
        }
        return result;
    }

    @GetMapping("/admin/users")
    public String adminUsers() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return "redirect:/ChatRoom";
        }
        return "admin-users";
    }

    @GetMapping("/api/admin/users")
    @ResponseBody
    public List<User> getAllUsers() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return new ArrayList<>();
        }
        List<User> users = userService.getAllUsers();
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    @PostMapping("/api/admin/update-role")
    @ResponseBody
    public Map<String, Object> updateRole(@RequestParam Integer id, @RequestParam String role) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = userService.getCurrentUser();

        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            result.put("success", false);
            result.put("message", "无权限");
            return result;
        }

        try {
            int rows = userService.updateUserRole(id, role);
            result.put("success", rows > 0);
            result.put("message", rows > 0 ? "修改成功" : "修改失败");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "修改失败：" + e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/admin/update-user")
    @ResponseBody
    public Map<String, Object> updateUser(@RequestParam Integer id,
                                          @RequestParam String name,
                                          @RequestParam String role) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = userService.getCurrentUser();

        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            result.put("success", false);
            result.put("message", "无权限");
            return result;
        }

        try {
            User existUser = userService.findByName(name);
            if (existUser != null && !existUser.getId().equals(id)) {
                result.put("success", false);
                result.put("message", "昵称已存在");
                return result;
            }

            User user = new User();
            user.setId(id);
            user.setName(name);
            user.setRole(role);
            int rows = userService.updateUser(user);

            result.put("success", rows > 0);
            result.put("message", rows > 0 ? "修改成功" : "修改失败");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "修改失败：" + e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/admin/delete-user")
    @ResponseBody
    public Map<String, Object> deleteUser(@RequestParam Integer id) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = userService.getCurrentUser();

        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            result.put("success", false);
            result.put("message", "无权限");
            return result;
        }

        if (id.equals(currentUser.getId())) {
            result.put("success", false);
            result.put("message", "不能删除自己");
            return result;
        }

        try {
            int rows = userService.deleteUser(id);
            result.put("success", rows > 0);
            result.put("message", rows > 0 ? "删除成功" : "删除失败");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }
}

