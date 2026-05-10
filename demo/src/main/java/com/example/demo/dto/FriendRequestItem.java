package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FriendRequestItem {
    private Long userId;           // 申请人ID
    private String username;       // 申请人用户名
    private LocalDateTime createdAt; // 申请时间
}