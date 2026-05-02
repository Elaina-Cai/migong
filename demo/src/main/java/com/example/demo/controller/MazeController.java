package com.example.demo.controller;

import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.dto.MoveRequest;
import com.example.demo.entity.Maze;
import com.example.demo.service.MazeService;
import com.example.demo.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/maze")
@RequiredArgsConstructor
public class MazeController {

    private final MazeService mazeService;

    // 生成迷宫
    @PostMapping("/generate")
    public Result<Maze> generate(HttpServletRequest request, @RequestBody MazeGenerateRequest genReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.generateMaze(userId, genReq));
    }
    // 获取当前迷宫
    @GetMapping("/current")
    public Result<Maze> getCurrent(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.getCurrentMaze(userId));
    }
    // 玩家移动
    @PostMapping("/move")
    public Result<Map<String, Object>> move(HttpServletRequest request, @RequestBody MoveRequest moveReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.movePlayer(userId, moveReq.getDirection()));
    }
    // 保存迷宫状态（实际上移动时已保存，这里保留方便未来扩展）
    @PostMapping("/save")
    public Result<?> save(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        mazeService.saveMazeState(userId);
        return Result.success();
    }
}