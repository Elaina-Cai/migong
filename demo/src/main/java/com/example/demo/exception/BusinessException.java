package com.example.demo.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;   // 业务错误码，如 400, 401, 403, 404, 500

    public BusinessException(String message) {
        this(500, message);  // 默认错误码 500
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}