package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.AuthServer;
import com.example.demo.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServerImpl implements AuthServer {
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    /**
     * 用户登录
     * @param request 登录请求（username, password）
     * @return JWT token
     */
    @Override
    public String login(LoginRequest request) {
        //TODO 1. 校验参数合法性
        String username = request.getUsername();
        String password = request.getPassword();
        // 2. 根据用户名和密码查询用户
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username",username).
                eq("password",password);
        User user = userMapper.selectOne(wrapper);
        if (user == null){
            //没在数据库中根据账号密码找到一行user，抛出自定义的业务异常
            throw new BusinessException(401,"用户名或密码错误");
        }
        // 3.验证通过，生成JWT token并返回去
        return jwtUtil.generateToken(user.getUserId(),user.getUsername());
    }
    /**
     * 用户注册
     * @param request 注册请求（username, password）
     * @return JWT token
     */
    @Override
    public String register(RegisterRequest request){
        //TODO 1. 校验参数合法性
        String username = request.getUsername();
        String password = request.getPassword();

        // 2.根据用户名查询用户
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username",username);
        if (userMapper.selectCount(wrapper)>0){
            throw new BusinessException(401,"该用户名已被注册");
        }
        // 3.验证通过，开始执行注册逻辑
        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);   //TODO 明文存储，后续可改为加密
        user.setStatus(1);            //默认正常
        // 插入数据库
        int result = userMapper.insert(user);
        if (result != 1) {
            throw new BusinessException(500, "注册失败，请稍后重试");
        }
        // 生成JWT token并返回去给前端保存(这就实现了注册之后自动登录的效果)
        return jwtUtil.generateToken(user.getUserId(), user.getUsername());
    }
}
