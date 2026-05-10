package com.example.demo.dto;

import lombok.Data;
import java.util.List;

/**
 * 给前端传输的单人模式排行榜的数据（只传输前XX名，迷宫的类型、长宽都由前端传过来，控制变量，按照通关耗时来排）
 * 这个类是统合很多个LeaderboardItem对象组成的列表 外加 当前登录用户的最佳成绩（可能为 null），在返回的时候是作为Result类的data字段的
 */
@Data
public class LeaderboardResponse {
    private List<LeaderboardItem> topList;   // 排行榜前50名
    private LeaderboardItem myRank;          // 当前登录用户的最佳成绩（可能为 null）
}