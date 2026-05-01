package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;

public interface AuthServer {
    String login(LoginRequest request);
    String register(RegisterRequest request);
    void logout(String token);
}
