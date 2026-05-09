package com.example.demo.service;

import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.entity.Maze;

import java.util.List;
import java.util.Map;

public interface MazeService {
    Maze generateMaze(Long userId, MazeGenerateRequest request);

    Maze getCurrentMaze(Long userId);

    Map<String, Object> movePlayer(Long userId, String direction);

    Maze saveMaze(Long userId, String mazeName);

    List<Maze> getSavedMazes(Long userId);

    Maze loadMaze(Long userId, Long mazeId, boolean saveCurrent, String currentMazeName);

    void deleteSavedMaze(Long userId, Long mazeId);
}