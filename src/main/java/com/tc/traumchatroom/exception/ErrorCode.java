package com.tc.traumchatroom.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    //HTTP层
    SUCCESS(200, HttpStatus.OK, "成功"),
    BAD_REQUEST(400, HttpStatus.BAD_REQUEST, "请求参数错误"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "未登录或Token过期"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(404, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(409, HttpStatus.CONFLICT, "数据冲突"),
    TOO_MANY_REQUESTS(429, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    SERVICE_UNAVAILABLE(503, HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用"),
    INTERNAL_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    // 用户相关
    USER_EXISTS(1001, HttpStatus.CONFLICT, "用户名已存在"),
    NAME_EXISTS(1002, HttpStatus.CONFLICT, "昵称已存在"),
    PASSWORD_WRONG(1003, HttpStatus.UNAUTHORIZED, "密码错误"),
    ACCOUNT_DISABLED(1004, HttpStatus.FORBIDDEN, "账号已被禁用"),

    // 好友相关
    ALREADY_FRIENDS(2001, HttpStatus.CONFLICT, "已经是好友"),
    REQUEST_EXISTS(2002, HttpStatus.CONFLICT, "好友申请已存在"),
    REQUEST_NOT_FOUND(2003, HttpStatus.NOT_FOUND, "好友申请不存在"),
    CANNOT_ADD_SELF(2004, HttpStatus.BAD_REQUEST, "不能添加自己为好友"),
    RECALL_TIMEOUT(2005, HttpStatus.CONFLICT, "超过撤回时间限制"),
    REQUEST_EXPIRED(2006, HttpStatus.GONE, "好友申请已过期"),

    // AI 相关
    AI_RATE_LIMIT(3001, HttpStatus.TOO_MANY_REQUESTS, "AI调用频率超限"),
    AI_TIMEOUT(3002, HttpStatus.GATEWAY_TIMEOUT, "AI回复超时"),

    // 文件相关
    FILE_TOO_LARGE(4001, HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED(4002, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型"),

    // 内容相关
    CONTENT_BLOCKED(4003, HttpStatus.UNPROCESSABLE_ENTITY, "消息包含违规内容");


    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

}
