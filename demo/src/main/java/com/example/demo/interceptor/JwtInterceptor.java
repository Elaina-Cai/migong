package com.example.demo.interceptor;

import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.UserMapper;
import com.example.demo.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;      // 注意：是 jakarta.servlet
import jakarta.servlet.http.HttpServletResponse;    // 注意：是 jakarta.servlet

@Slf4j
@Component
@RequiredArgsConstructor
//jwt格式token的拦截器的模板
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    //拦截器判断是否要拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        // 1.获取 Authorization 头
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("未提供有效的 Authorization 头");
            throw new BusinessException(401, "未登录或 token 已过期");
        }
        // 2.验证 token
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            log.warn("token 验证失败: {}", token);
            throw new BusinessException(401, "token 无效或已过期");
        }
        // 3.检查用户状态(注：token是否有效的代码由JwtUtil中的方法来判断，而用户账号是否被管理员“纳入黑名单”要在拦截器这里进行判断)
        //从 token 中获取用户 ID
        Long userId = jwtUtil.getUserIdFromToken(token);
        // 判断用户状态
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户状态异常，userId: {}, status: {}", userId, user != null ? user.getStatus() : "null");
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        //将user存入 request->Attribute中 供 Controller 使用
        request.setAttribute("userId", userId);
        return true;
    }
}