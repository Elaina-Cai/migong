package com.example.demo.service;

import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.entity.Maze;

import java.util.Map;
//迷宫服务（包括生成迷宫和移动玩家位置）
public interface MazeService {
    Maze generateMaze(Long userId, MazeGenerateRequest request);

    Maze getCurrentMaze(Long userId);

    Map<String, Object> movePlayer(Long userId, String direction);

    void saveMazeState(Long userId);
}