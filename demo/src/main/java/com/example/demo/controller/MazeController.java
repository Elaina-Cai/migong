package com.example.demo.controller;

import com.example.demo.dto.LoadMazeRequest;
import com.example.demo.dto.MazeGenerateRequest;
import com.example.demo.dto.MoveRequest;
import com.example.demo.dto.SaveMazeRequest;
import com.example.demo.entity.Maze;
import com.example.demo.service.MazeService;
import com.example.demo.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maze")
@RequiredArgsConstructor
public class MazeController {

    private final MazeService mazeService;

    @PostMapping("/generate")
    public Result<Maze> generate(HttpServletRequest request, @RequestBody MazeGenerateRequest genReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.generateMaze(userId, genReq));
    }

    @GetMapping("/current")
    public Result<Maze> getCurrent(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.getCurrentMaze(userId));
    }

    @PostMapping("/move")
    public Result<Map<String, Object>> move(HttpServletRequest request, @RequestBody MoveRequest moveReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.movePlayer(userId, moveReq.getDirection()));
    }

    @PostMapping("/save")
    public Result<Maze> save(HttpServletRequest request, @RequestBody SaveMazeRequest saveReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.saveMaze(userId, saveReq.getMazeName()));
    }

    @GetMapping("/saved")
    public Result<List<Maze>> getSaved(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Maze> list = mazeService.getSavedMazes(userId);
        list.forEach(m -> {
            m.setGridData(null);
            m.setItemPositions(null);
        });
        return Result.success(list);
    }

    @PostMapping("/load")
    public Result<Maze> load(HttpServletRequest request, @RequestBody LoadMazeRequest loadReq) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(mazeService.loadMaze(userId, loadReq.getMazeId(),
                loadReq.isSaveCurrent(), loadReq.getCurrentMazeName()));
    }

    @DeleteMapping("/saved/{id}")
    public Result<?> deleteSaved(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        mazeService.deleteSavedMaze(userId, id);
        return Result.success();
    }
}
