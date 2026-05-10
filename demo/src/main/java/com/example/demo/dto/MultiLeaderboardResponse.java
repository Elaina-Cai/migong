package com.example.demo.dto;

import lombok.Data;
import java.util.List;

/**
 * 多人模式的排行榜的全部数据，要装到Result类里再返回给前端（有所需要的排行榜 + 当前用户的排名）
 */
@Data
public class MultiLeaderboardResponse {
    private List<MultiLeaderboardItem> topList;
    private MultiLeaderboardItem myRank;
}