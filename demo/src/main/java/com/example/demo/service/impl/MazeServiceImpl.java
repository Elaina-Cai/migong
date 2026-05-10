package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.dto.LeaderboardItem;
import com.example.demo.dto.LeaderboardResponse;
import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.entity.Maze;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.MazeMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.MazeService;
import com.example.demo.utils.MazeGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MazeServiceImpl implements MazeService {

    private final UserMapper userMapper;
    private final MazeMapper mazeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_SAVED_MAZES = 10;

    @Override
    public Maze generateMaze(Long userId, MazeGenerateRequest request) {
        int rows = request.getRows() == null ? 21 : request.getRows();
        int cols = request.getCols() == null ? 21 : request.getCols();
        String algo = request.getAlgorithm() == null ? "dfs" : request.getAlgorithm();

        int[][] grid = MazeGenerator.generate(rows, cols, algo);

        grid[1][0] = 0;
        grid[grid.length - 2][grid[0].length - 1] = 0;

        String gridJson;
        try {
            gridJson = objectMapper.writeValueAsString(grid);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "迷宫数据序列化失败");
        }

        // 删除未保存的迷宫
        LambdaQueryWrapper<Maze> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(Maze::getUserId, userId).eq(Maze::getIsSaved, 0);
        mazeMapper.delete(deleteWrapper);

        // 将已保存的进行中迷宫归档（status -> 0）
        LambdaUpdateWrapper<Maze> archiveWrapper = new LambdaUpdateWrapper<>();
        archiveWrapper.eq(Maze::getUserId, userId)
                .eq(Maze::getIsSaved, 1)
                .eq(Maze::getStatus, 1)
                .set(Maze::getStatus, 0);
        mazeMapper.update(null, archiveWrapper);

        Maze maze = new Maze();
        maze.setUserId(userId);
        maze.setRowsNum(grid.length);
        maze.setColsNum(grid[0].length);
        maze.setAlgorithm(algo);
        maze.setGridData(gridJson);
        maze.setPlayerRow(1);
        maze.setPlayerCol(0);
        maze.setStartRow(1);
        maze.setStartCol(0);
        maze.setEndRow(grid.length - 2);
        maze.setEndCol(grid[0].length - 1);
        maze.setItemPositions("[]");
        maze.setStatus(1);
        maze.setIsSaved(0);
        maze.setStartTime(LocalDateTime.now());
        maze.setElapsedSeconds(null);
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

        if (newRow < 0 || newRow >= grid.length || newCol < 0 || newCol >= grid[0].length) {
            throw new BusinessException(400, "撞墙了！");
        }
        if (grid[newRow][newCol] == 1) {
            throw new BusinessException(400, "撞墙了！");
        }

        maze.setPlayerRow(newRow);
        maze.setPlayerCol(newCol);
        mazeMapper.updateById(maze);

        boolean won = (newRow == maze.getEndRow() && newCol == maze.getEndCol());
        if (won) {
            maze.setStatus(2);
            // 计算耗时
            if (maze.getStartTime() != null) {
                long seconds = java.time.Duration.between(
                        maze.getStartTime(), LocalDateTime.now()).getSeconds();
                maze.setElapsedSeconds((int) seconds);
            }
            mazeMapper.updateById(maze);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("row", newRow);
        result.put("col", newCol);
        result.put("won", won);
        if (won) {
            result.put("elapsedSeconds", maze.getElapsedSeconds()); // ← 添加这一行
        }
        return result;
    }

    @Override
    public Maze saveMaze(Long userId, String mazeName) {
        if (mazeName == null || mazeName.trim().isEmpty()) {
            throw new BusinessException(400, "存档名称不能为空");
        }
        // 查找用户最近一个未保存的迷宫（包括通关状态）
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getUserId, userId)
                .eq(Maze::getIsSaved, 0)
                .orderByDesc(Maze::getId)
                .last("LIMIT 1");
        Maze maze = mazeMapper.selectOne(wrapper);
        if (maze == null) {
            throw new BusinessException(404, "没有可保存的迷宫，请先生成新迷宫");
        }

        if (maze.getIsSaved() == null || maze.getIsSaved() == 0) {
            LambdaQueryWrapper<Maze> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(Maze::getUserId, userId).eq(Maze::getIsSaved, 1);
            Long count = mazeMapper.selectCount(countWrapper);
            if (count >= MAX_SAVED_MAZES) {
                throw new BusinessException(400, "存档数量已达上限（" + MAX_SAVED_MAZES + "个），请先删除旧存档");
            }
        }

        maze.setIsSaved(1);
        maze.setMazeName(mazeName.trim());
        maze.setSavedAt(LocalDateTime.now());
        mazeMapper.updateById(maze);
        return maze;
    }

    @Override
    public List<Maze> getSavedMazes(Long userId) {
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getUserId, userId)
                .eq(Maze::getIsSaved, 1)
                .orderByDesc(Maze::getSavedAt);
        return mazeMapper.selectList(wrapper);
    }

    @Override
    public Maze loadMaze(Long userId, Long mazeId, boolean saveCurrent, String currentMazeName) {
        // 校验目标存档存在
        LambdaQueryWrapper<Maze> targetWrapper = new LambdaQueryWrapper<>();
        targetWrapper.eq(Maze::getId, mazeId)
                .eq(Maze::getUserId, userId)
                .eq(Maze::getIsSaved, 1);
        Maze target = mazeMapper.selectOne(targetWrapper);
        if (target == null) {
            throw new BusinessException(404, "存档不存在");
        }

        // 处理当前进行中的迷宫
        LambdaQueryWrapper<Maze> currentWrapper = new LambdaQueryWrapper<>();
        currentWrapper.eq(Maze::getUserId, userId).eq(Maze::getStatus, 1);
        Maze currentMaze = mazeMapper.selectOne(currentWrapper);

        if (currentMaze != null && !currentMaze.getId().equals(mazeId)) {
            if (saveCurrent) {
                if (currentMazeName == null || currentMazeName.trim().isEmpty()) {
                    throw new BusinessException(400, "请输入当前迷宫的存档名称");
                }
                LambdaQueryWrapper<Maze> countWrapper = new LambdaQueryWrapper<>();
                countWrapper.eq(Maze::getUserId, userId).eq(Maze::getIsSaved, 1);
                Long count = mazeMapper.selectCount(countWrapper);
                if (count >= MAX_SAVED_MAZES) {
                    throw new BusinessException(400, "存档数量已达上限，无法保存当前迷宫");
                }
                currentMaze.setIsSaved(1);
                currentMaze.setMazeName(currentMazeName.trim());
                currentMaze.setSavedAt(LocalDateTime.now());
                currentMaze.setStatus(0);
                mazeMapper.updateById(currentMaze);
            } else {
                if (currentMaze.getIsSaved() != null && currentMaze.getIsSaved() == 1) {
                    currentMaze.setStatus(0);
                    mazeMapper.updateById(currentMaze);
                } else {
                    mazeMapper.deleteById(currentMaze.getId());
                }
            }
        }

        // 加载目标存档
        if (target.getStatus() != null && target.getStatus() == 2) {
            target.setPlayerRow(target.getStartRow());
            target.setPlayerCol(target.getStartCol());
            target.setStatus(1);
        } else {
            target.setStatus(1);
        }
        mazeMapper.updateById(target);
        return target;
    }

    @Override
    public void deleteSavedMaze(Long userId, Long mazeId) {
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getId, mazeId)
                .eq(Maze::getUserId, userId)
                .eq(Maze::getIsSaved, 1);
        Maze maze = mazeMapper.selectOne(wrapper);
        if (maze == null) {
            throw new BusinessException(404, "存档不存在");
        }
        mazeMapper.deleteById(mazeId);
    }

    private int[][] parseGridData(String json) {
        try {
            return objectMapper.readValue(json, int[][].class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "迷宫数据解析失败");
        }
    }

    @Override
    public LeaderboardResponse getLeaderboard(Long currentUserId, String algorithm, Integer rows, Integer cols) {
        // 1. 构建查询条件
        LambdaQueryWrapper<Maze> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Maze::getStatus, 2)
                .eq(Maze::getIsSaved, 1);

        if (algorithm != null && !algorithm.trim().isEmpty()) {
            wrapper.eq(Maze::getAlgorithm, algorithm.trim());
        }
        if (rows != null) {
            wrapper.eq(Maze::getRowsNum, rows);
        }
        if (cols != null) {
            wrapper.eq(Maze::getColsNum, cols);
        }

        // 2. 排序并截取前50
        wrapper.orderByAsc(Maze::getElapsedSeconds)
                .last("LIMIT 50");

        List<Maze> mazeList = mazeMapper.selectList(wrapper);

        // 3. 收集所有 userId，批量查询用户
        List<Long> userIds = mazeList.stream()
                .map(Maze::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long,String> usernameMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername));

        // 4. 组装 topList
        List<LeaderboardItem> topList = new ArrayList<>();
        for (int i = 0; i < mazeList.size(); i++) {
            Maze m = mazeList.get(i);
            LeaderboardItem item = new LeaderboardItem();
            item.setRank(i + 1);
            item.setUserId(m.getUserId());
            item.setUsername(usernameMap.getOrDefault(m.getUserId(), "未知用户"));
            item.setMazeName(m.getMazeName());
            item.setElapsedSeconds(m.getElapsedSeconds());
            item.setAlgorithm(m.getAlgorithm());
            item.setRowsNum(m.getRowsNum());
            item.setColsNum(m.getColsNum());
            item.setSavedAt(m.getSavedAt());
            topList.add(item);
        }

        // 5. 查询当前用户的最佳记录
        LeaderboardItem myRank = null;
        if (currentUserId != null) {
            LambdaQueryWrapper<Maze> myWrapper = new LambdaQueryWrapper<>();
            myWrapper.eq(Maze::getUserId, currentUserId)
                    .eq(Maze::getStatus, 2)
                    .eq(Maze::getIsSaved, 1);
            if (algorithm != null && !algorithm.trim().isEmpty()) {
                myWrapper.eq(Maze::getAlgorithm, algorithm.trim());
            }
            if (rows != null) myWrapper.eq(Maze::getRowsNum, rows);
            if (cols != null) myWrapper.eq(Maze::getColsNum, cols);
            myWrapper.orderByAsc(Maze::getElapsedSeconds)
                    .last("LIMIT 1");

            Maze myBest = mazeMapper.selectOne(myWrapper);
            if (myBest != null) {
                // 计算当前用户在相同筛选条件下的排名
                LambdaQueryWrapper<Maze> countWrapper = new LambdaQueryWrapper<>();
                countWrapper.eq(Maze::getStatus, 2)
                        .eq(Maze::getIsSaved, 1)
                        .lt(Maze::getElapsedSeconds, myBest.getElapsedSeconds());
                if (algorithm != null && !algorithm.trim().isEmpty()) {
                    countWrapper.eq(Maze::getAlgorithm, algorithm.trim());
                }
                if (rows != null) countWrapper.eq(Maze::getRowsNum, rows);
                if (cols != null) countWrapper.eq(Maze::getColsNum, cols);
                long count = mazeMapper.selectCount(countWrapper);
                myRank = new LeaderboardItem();
                myRank.setRank((int) count + 1);
                myRank.setUserId(currentUserId);
                myRank.setUsername("我");  // 前端可替换为实际用户名
                myRank.setMazeName(myBest.getMazeName());
                myRank.setElapsedSeconds(myBest.getElapsedSeconds());
                myRank.setAlgorithm(myBest.getAlgorithm());
                myRank.setRowsNum(myBest.getRowsNum());
                myRank.setColsNum(myBest.getColsNum());
                myRank.setSavedAt(myBest.getSavedAt());
            }
        }

        // 6. 组装返回体
        LeaderboardResponse response = new LeaderboardResponse();
        response.setTopList(topList);
        response.setMyRank(myRank);
        return response;
    }
}
