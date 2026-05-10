package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用于代表单人模式排行榜中的一行数据
 */
@Data
public class LeaderboardItem {
    private int rank;               // 排名
    private Long userId;            // 用户ID
    private String username;        // 用户名
    private String mazeName;        // 迷宫名称
    private int elapsedSeconds;     // 通关耗时（秒）
    private String algorithm;       // 生成算法
    private int rowsNum;            // 迷宫行数
    private int colsNum;            // 迷宫列数
    private LocalDateTime savedAt;  // 保存时间（通关日期）
}