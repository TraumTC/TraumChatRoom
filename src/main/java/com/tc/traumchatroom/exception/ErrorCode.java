package com.tc.traumchatroom.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    //HTTP层
    SUCCESS(200,"成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户相关
    USER_EXISTS(1001, "用户名已存在"),
    NAME_EXISTS(1002, "昵称已存在"),
    PASSWORD_WRONG(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),

    // 好友相关
    ALREADY_FRIENDS(2001, "已经是好友"),
    REQUEST_EXISTS(2002, "好友申请已存在"),
    REQUEST_NOT_FOUND(2003, "好友申请不存在"),
    CANNOT_ADD_SELF(2004, "不能添加自己为好友"),
    RECALL_TIMEOUT(2005, "超过撤回时间限制"),
    REQUEST_EXPIRED(2006, "好友申请已过期"),

    // AI 相关
    AI_RATE_LIMIT(3001, "AI调用频率超限"),
    AI_TIMEOUT(3002, "AI回复超时"),

    // 文件相关
    FILE_TOO_LARGE(4001, "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED(4002, "不支持的文件类型"),

    // 内容相关
    CONTENT_BLOCKED(4003, "消息包含违规内容");


    private  final  int code;
    private final String message;

}
