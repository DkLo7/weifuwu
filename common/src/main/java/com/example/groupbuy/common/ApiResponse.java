package com.example.groupbuy.common;

public class ApiResponse<T> {
    public int code;
    public String message;
    public T data;

    /** 创建统一响应。 */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 包装成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data);
    }
}
