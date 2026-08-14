package com.tc.traumchatroom.config;

import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private HttpServletRequest request;

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常:{}",e.getMessage());
        return Result.error(e.getErrorCode(),e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation (MethodArgumentNotValidException e) {
        // 只返回首个校验错误，避免多个字段错误拼接过长
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("参数校验失败");
        return new Result<>(400, msg, null);
    }

    /** 参数类型不匹配 / 缺少参数 / JSON 解析失败 / 绑定失败 → 400（而非 500） */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            BindException.class})
    public Result<?> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST);
    }

    /** 404：静态资源/接口不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNotFound(NoResourceFoundException e) {
        return Result.error(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 兜底未知异常：记录请求 URL，便于生产定位是哪个接口出问题
        log.error("未知异常: {}", request.getRequestURI(), e);
        return  Result.error(ErrorCode.INTERNAL_ERROR);
    }
}
