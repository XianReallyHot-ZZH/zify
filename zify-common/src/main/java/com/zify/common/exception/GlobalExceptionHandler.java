package com.zify.common.exception;

import com.zify.common.web.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 请求参数校验失败（@RequestBody @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 请求参数绑定失败（表单参数 / Query 参数）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Bind failed: {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 请求参数校验失败（@RequestParam @Validated / @PathVariable 约束）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * JSON 请求体解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("JSON parse failed: {}", e.getMessage());
        return Result.fail(ErrorCode.JSON_PARSE_ERROR.getCode(), ErrorCode.JSON_PARSE_ERROR.getMessage());
    }

    /**
     * 路由不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("No handler found: method={}, url={}", e.getHttpMethod(), e.getRequestURL());
        return Result.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /**
     * 兜底：未预期的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
