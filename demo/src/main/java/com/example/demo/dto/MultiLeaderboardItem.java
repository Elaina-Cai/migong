package com.example.demo.dto;

import lombok.Data;

/**
 * 多人模式的排行榜的每一行数据，要先装到MultiLeaderboardResponse类里，然后再装到Result类里返回给前端
 */
@Data
public class MultiLeaderboardItem {
    private int rank;
    private Long userId;
    private String username;
    private int wins;              // 获胜次数
    private int fastestTime;       // 最快获胜时间（秒）
}