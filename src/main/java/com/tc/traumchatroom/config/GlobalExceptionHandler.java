package com.tc.traumchatroom.config;

import com.tc.traumchatroom.dto.response.Result;
import com.tc.traumchatroom.exception.BusinessException;
import com.tc.traumchatroom.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        log.warn("业务异常: method={}, uri={}, code={}, status={}, message={}",
                request.getMethod(), request.getRequestURI(), code.getCode(),
                code.getHttpStatus().value(), e.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(Result.error(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidation(MethodArgumentNotValidException e,
                                                        HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("参数校验失败");
        log.warn("参数校验失败: method={}, uri={}, field={}, message={}",
                request.getMethod(), request.getRequestURI(),
                e.getBindingResult().getFieldErrors().stream().findFirst()
                        .map(f -> f.getField()).orElse("unknown"), msg);
        return response(ErrorCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            BindException.class,
            ConstraintViolationException.class,
            MultipartException.class})
    public ResponseEntity<Result<?>> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("请求参数错误: method={}, uri={}, exception={}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        return response(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleFileTooLarge(MaxUploadSizeExceededException e,
                                                         HttpServletRequest request) {
        log.warn("上传文件过大: method={}, uri={}, maxSize={}",
                request.getMethod(), request.getRequestURI(), e.getMaxUploadSize());
        return response(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataConflict(DataIntegrityViolationException e,
                                                         HttpServletRequest request) {
        log.warn("数据库约束冲突: method={}, uri={}", request.getMethod(), request.getRequestURI());
        log.debug("数据库约束冲突详情", e);
        return response(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getMessage());
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<Result<?>> handleRedisUnavailable(Exception e, HttpServletRequest request) {
        log.error("Redis 服务不可用: method={}, uri={}", request.getMethod(), request.getRequestURI(), e);
        return response(ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("访问被拒绝: method={}, uri={}", request.getMethod(), request.getRequestURI());
        return response(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("资源不存在: method={}, uri={}", request.getMethod(), request.getRequestURI());
        return response(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常: method={}, uri={}, exception={}",
                request.getMethod(), request.getRequestURI(), e.getClass().getName(), e);
        return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<Result<?>> response(ErrorCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.error(code, message));
    }
}
