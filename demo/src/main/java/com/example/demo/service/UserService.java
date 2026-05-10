package com.example.demo.service;

import com.example.demo.dto.UserSearchResult;

import java.util.List;

public interface UserService {
    List<UserSearchResult> searchByUsername(String keyword, Long currentUserId);
}