package com.example.groupbuy.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorAdvice {
    /** 将业务异常映射为统一错误响应。 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> business(ApiException error) {
        return ResponseEntity.status(error.status).body(new ApiResponse<>(error.status, error.getMessage(), null));
    }

    /** 将参数校验错误映射为统一响应。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, error.getBindingResult().getFieldErrors().get(0).getDefaultMessage(), null));
    }

    /** 避免将内部异常细节返回给前端。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception error) {
        return ResponseEntity.status(500).body(new ApiResponse<>(500, "服务暂时不可用", null));
    }
}
