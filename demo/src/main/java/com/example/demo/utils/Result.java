package com.example.demo.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data               //相当于 @Getter @Setter @ToString @EqualsAndHashCode
@NoArgsConstructor  //无参构造
@AllArgsConstructor //全参构造
public class Result <T>{
    private Integer code;
    private String message;
    private T data;   //泛型数据
    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200,"success",null);
    }
    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }
    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }
    /**
     * 失败响应（默认消息）
     */
    public static <T> Result<T> fail() {
        return new Result<>(500, "系统繁忙，请稍后重试",null);
    }
    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message,null);
    }
    /**
     * 失败响应（自定义状态码和消息）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
