package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("friend")
public class Friend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;        // 申请方（或关系一方）
    private Long friendId;      // 对方用户ID
    private Integer status;     // 1=正常
    private LocalDateTime createdAt;
}