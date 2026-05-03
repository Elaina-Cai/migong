package com.example.demo.config;

import com.example.demo.websocket.MazeWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    //注入 MazeWebSocketHandler 的 Bean，这样 Handler 就能使用 Spring 管理的依赖（如 JwtUtil）
    @Autowired
    private MazeWebSocketHandler mazeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mazeWebSocketHandler, "/ws/maze")
                .setAllowedOrigins("*");   // 开发阶段允许所有来源
    }
}