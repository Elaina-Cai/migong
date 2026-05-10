package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("multi_game_record")
public class MultiGameRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;           // 获胜者用户ID
    private String roomId;         // 房间号
    private Integer elapsedSeconds; // 通关用时（秒）
    private Integer playerCount;    // 参与人数
    private LocalDateTime playedAt; // 游戏结束时间
}