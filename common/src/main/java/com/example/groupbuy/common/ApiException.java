package com.example.groupbuy.common;

public class ApiException extends RuntimeException {
    public final int status;

    /** 创建带 HTTP 状态码的业务异常。 */
    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
