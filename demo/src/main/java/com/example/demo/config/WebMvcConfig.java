package com.example.demo.config;

import com.example.demo.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    //注册一个jwt格式token的拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")               // 拦截所有路径
                .excludePathPatterns(
                        "/auth/login",                // 登录接口
                        "/auth/register",             // 注册接口
                        "/error",                     // Spring Boot 默认错误路径
                        "/swagger-ui/**",             // 如果有 Swagger
                        "/v3/api-docs/**",
                        "/",                 // 根路径(index.html)
                        "/index.html",
                        "/favicon.ico",
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/*.html",
                        "/*.css",
                        "/*.js"
                );
    }
}