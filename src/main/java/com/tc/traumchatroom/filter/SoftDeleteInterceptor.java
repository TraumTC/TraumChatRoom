package com.tc.traumchatroom.filter;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;

/**
 * MyBatis 软删除拦截器
 *
 * 注意：此拦截器暂未启用，因为会误匹配不带 deleted_at 字段的表
 * 软删除条件已手动加在各 Mapper XML 的查询语句中
 *
 * 后续优化：可以通过检查表的列信息来判断是否需要追加条件
 */
// @Component  // 暂不启用，避免误匹配
// @Intercepts({
//     @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
// })
public class SoftDeleteInterceptor {
    // 实现保留，后续优化后启用
}
