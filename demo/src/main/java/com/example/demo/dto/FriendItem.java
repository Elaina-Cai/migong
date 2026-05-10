package com.example.demo.dto;

import lombok.Data;

@Data
public class FriendItem {
    private Long userId;
    private String username;
    private boolean online;    // 是否在线
}