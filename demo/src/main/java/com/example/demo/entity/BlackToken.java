package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("token_blacklist")
public class BlackToken {
    private String tokenHash;           //token的SHA256(token) 的十六进制字符串（固定 64 字符）
    private LocalDateTime expireTime;   //过期时间
}
