package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.entity.Maze;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.MazeMapper;
import com.example.demo.service.MazeService;
import com.example.demo.utils.MazeGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MazeServiceImpl implements MazeService {

    private final MazeMapper mazeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Maze generateMaze(Long userId, MazeGenerateRequest request) {
        int rows = request.getRows() == null ? 21 : request.getRows();
        int cols = request.getCols() == null ? 21 : request.getCols();
        String algo = request.getAlgorithm() == null ? "dfs" : request.getAlgorithm();

        int[][] grid = MazeGenerator.generate(rows, cols, algo);

        // 挖开入口和出口的墙壁
        grid[1][0] = 0;                              // 入口
        grid[grid.length - 2][grid[0].length - 1] = 0; // 出口

        String gridJson;
        try {
            gridJson = objectMapper.writeValueAsString(grid);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "迷宫数据序列化失败");
        }

        Maze maze = new Maze();
        maze.setUserId(userId);
        maze.setRowsNum(grid.length);
        maze.setColsNum(grid[0].length);
        maze.setAlgorithm(algo);
        maze.setGridData(gridJson);
        maze.setPlayerRow(1);
        maze.setPlayerCol(0); // 入口在 (1,0)，起点左边墙开口
        maze.setStartRow(1);
        maze.setStartCol(0);
        maze.setEndRow(grid.length-2);
        maze.setEndCol(grid[0].length-1); // 出口
        maze.setItemPositions("[]");
        maze.setStatus(1);
        // 删除旧迷宫，保存新迷宫（每个用户只保留一个进行中的迷宫）
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getUserId, userId);
        mazeMapper.delete(wrapper);
        mazeMapper.insert(maze);

        return maze;
    }
    @Override
    public Maze getCurrentMaze(Long userId) {
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getUserId, userId).eq(Maze::getStatus, 1);
        Maze maze = mazeMapper.selectOne(wrapper);
        if (maze == null) {
            throw new BusinessException(404, "没有进行中的迷宫，请先生成新迷宫");
        }
        return maze;
    }
    @Override
    public Map<String, Object> movePlayer(Long userId, String direction) {
        Maze maze = getCurrentMaze(userId);
        int[][] grid = parseGridData(maze.getGridData());
        int newRow = maze.getPlayerRow();
        int newCol = maze.getPlayerCol();

        switch (direction.toLowerCase()) {
            case "up": newRow--; break;
            case "down": newRow++; break;
            case "left": newCol--; break;
            case "right": newCol++; break;
            default: throw new BusinessException(400, "无效的方向");
        }

        // 边界与墙壁检测
        if (newRow < 0 || newRow >= grid.length || newCol < 0 || newCol >= grid[0].length) {
            throw new BusinessException(400, "撞墙了！");
        }
        if (grid[newRow][newCol] == 1) {
            throw new BusinessException(400, "撞墙了！");
        }

        // 更新位置
        maze.setPlayerRow(newRow);
        maze.setPlayerCol(newCol);
        mazeMapper.updateById(maze);

        // 检查是否到达终点
        boolean won = (newRow == maze.getEndRow() && newCol == maze.getEndCol());
        if (won) {
            maze.setStatus(2);
            mazeMapper.updateById(maze);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("row", newRow);
        result.put("col", newCol);
        result.put("won", won);
        return result;
    }
    private int[][] parseGridData(String json) {
        try {
            return objectMapper.readValue(json, int[][].class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "迷宫数据解析失败");
        }
    }
    @Override
    public void saveMazeState(Long userId) {
        // 当前实现中每次移动即更新，save 可省略或用于强制刷新
        getCurrentMaze(userId);
    }
}