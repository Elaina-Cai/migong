package com.example.demo.dto;

import lombok.Data;

@Data
public class UserSearchResult {
    private Long userId;
    private String username;
    private String friendStatus;
}