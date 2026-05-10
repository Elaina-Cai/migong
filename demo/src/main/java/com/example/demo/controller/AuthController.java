package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.mapper.OnlineUserMapper;
import com.example.demo.service.AuthServer;
import com.example.demo.utils.JwtUtil;
import com.example.demo.utils.Result;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static com.example.demo.utils.JwtUtil.extractToken;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthServer authServer;
    private final OnlineUserMapper onlineUserMapper;   // 新增
    private final JwtUtil jwtUtil;

    /**
     * 用户登录api
     * POST /login
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request){
        //TODO 1.记录收到的请求到日志中
        //2.调用service层的逻辑（以下是成功了的，如果登陆失败，service层会抛出异常并被全局处理器拦截，直接返回错误 Result 给前端）
        String token = authServer.login(request);
        //3.解析 token 获取 userId 和 username
        Long userId = jwtUtil.getUserIdFromToken(token);  // 需要注入 JwtUtil
        String username = jwtUtil.getUsernameFromToken(token);
        //4.写入 online_users 表
        onlineUserMapper.upsertOnline(userId, username);
        //5.包装成Result类并返回给前端
        return Result.success(token);
    }
    /**
     * 用户注册api
     * POST /register
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest request){
        //TODO 1.记录收到的请求到日志中
        //2.调用service层业务逻辑
        String token = authServer.register(request);
        //3.包装成Result类并返回给前端
        return Result.success(token);
    }
    /**
     * 用户登出api
     * POST /logout
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization")String authHeader){//把http请求头的Authorization头放到authHeader中
        //TODO 1.记录收到的请求到日志中
        //2.提取出纯的token
        String token = extractToken(authHeader);
        //2.从 token 中获取 userId 后再登出
        Long userId = jwtUtil.getUserIdFromToken(token);   // 需要注入 JwtUtil
        //3.调用service层的业务逻辑
        authServer.logout(token);
        //4.别忘了从 online_users 表删除
        onlineUserMapper.deleteById(userId);
        return Result.success();
    }
}
