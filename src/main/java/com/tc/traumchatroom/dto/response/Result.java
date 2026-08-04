package com.tc.traumchatroom.dto.response;

import com.tc.traumchatroom.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200,"success",data);
    }
    public static <T> Result<T> success() {
        return new Result<>(200,"success",null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(),errorCode.getMessage(),null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<> (errorCode.getCode(),message,null);
    }
}
