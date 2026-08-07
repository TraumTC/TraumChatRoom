package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 */
public interface UserMapper {

    /** 根据ID查询用户 */
    User findById(@Param("id") Integer id);

    /** 根据用户名查询用户（登录用） */
    User findByUsername(@Param("username") String username);

    /** 根据昵称查询用户 */
    User findByName(@Param("name") String name);

    /** 插入新用户（注册） */
    int insert(User user);

    /** 插入用户（冲突忽略，用于 AI 用户等幂等创建） */
    int insertIgnore(User user);

    /** 修改用户资料（昵称、头像） */
    int updateProfile(User user);

    /** 修改密码 */
    int updatePassword(@Param("id") Integer id, @Param("password") String password);

    /** 修改用户角色（管理员） */
    int updateRole(@Param("id") Integer id, @Param("role") String role);

    /** 修改用户状态（禁用/启用） */
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    /** 修改头像 */
    int updateAvatar(@Param("id") Integer id, @Param("avatar") String avatar);

    /** 更新最后活跃时间 */
    int updateLastActiveTime(@Param("id") Integer id);

    /** 软删除用户 */
    int softDelete(@Param("id") Integer id);

    /** 查询所有用户（管理员分页，支持包含已删除） */
    List<User> findAll(@Param("keyword") String keyword,
                       @Param("includeDeleted") boolean includeDeleted,
                       @Param("offset") int offset,
                       @Param("size") int size);

    /** 查询用户总数（管理员分页） */
    int countAll(@Param("keyword") String keyword,
                 @Param("includeDeleted") boolean includeDeleted);

    /** 搜索用户（好友搜索，排除自己） */
    List<User> searchUsers(@Param("keyword") String keyword,
                           @Param("excludeId") Integer excludeId,
                           @Param("limit") int limit);

    /** 按 ID 集合批量查询用户（优化 N+1） */
    List<User> findByIds(@Param("ids") List<Integer> ids);
}
