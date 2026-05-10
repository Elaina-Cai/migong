package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("online_users")
public class OnlineUser {
    @TableId
    private Long userId;
    private String username;
    private LocalDateTime lastActive;
}