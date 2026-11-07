package com.reapnow.common;

import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getCode() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 缺少 X-Token 请求头时返回 401 而不是 500。
     * 否则前端拿到「服务器错误」而非「未登录」，会跳不到登录页。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "未登录，请先登录后再操作"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数错误");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(400, msg));
    }

    /**
     * 跨服务调用超时 —— C3「超时与错误传播」。
     *
     * Feign 在读取超时后抛 RetryableException（底层是 SocketTimeoutException）。
     * 如果落到通用 Exception 处理，用户会看到「服务器错误: Read timed out」这种英文堆栈，
     * 既看不懂也不知道该怎么办。这里单独识别并给出可执行的中文提示。
     */
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignTimeout(RetryableException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(504, "依赖服务响应超时，请稍后重试"));
    }

    @ExceptionHandler(java.net.SocketTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocketTimeout(java.net.SocketTimeoutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error(504, "依赖服务响应超时，请稍后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器错误: " + e.getMessage()));
    }
}
