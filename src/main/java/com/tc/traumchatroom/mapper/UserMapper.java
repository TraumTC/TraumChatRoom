package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    public List<User> findAll();
    public User findByUsername(String username);
    public User findByName(String name);
    public int addUser(User account);
    public int updateUser(User account);
    public int deleteUser(Integer id);
    public int updateNameAndPassword(User account);
    public int updateRole(Integer id, String role);
}
