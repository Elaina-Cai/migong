package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.service.AuthServer;
import com.example.demo.utils.Result;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthServer authServer;
    /**
     * 用户登录api
     * POST /login
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request){
        //TODO 1.记录收到的请求到日志中
        //2.调用service层的逻辑（以下是成功了的，如果登陆失败，service层会抛出异常并被全局处理器拦截，直接返回错误 Result 给前端）
        String token = authServer.login(request);
        //3.包装成Result类并返回给前端
        return Result.success(token);
    }
    /**
     * 用户注册api
     * POST /login
     */
    @PostMapping("/register")
    public Result register(@RequestBody RegisterRequest request){
        //TODO 1.记录收到的请求到日志中
        //2.调用service层业务逻辑
        String token = authServer.register(request);
        //3.包装成Result类并返回给前端
        return Result.success(token);
    }
}
