package com.example.demo.service;

import com.example.demo.dto.MultiLeaderboardResponse;

public interface MultiGameService {
    // 记录一局游戏的获胜者
    void recordWin(String roomId, String winnerUserIdStr, int elapsedSeconds, int playerCount);

    // 查询多人排行榜
    MultiLeaderboardResponse getMultiLeaderboard(Long currentUserId);
}