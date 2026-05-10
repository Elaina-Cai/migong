package com.example.demo.controller;

import com.example.demo.mapper.OnlineUserMapper;
import com.example.demo.utils.Result;
import com.example.demo.websocket.MazeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返回在线总人数（前端可能在某个位置显示“当前在线：x 人”）
 */
@RestController
@RequiredArgsConstructor
public class OnlineController {

    private final OnlineUserMapper onlineUserMapper;

    @GetMapping("/online-count")
    public Result<Integer> onlineCount() {
        long count = onlineUserMapper.selectCount(null);
        return Result.success((int) count);
    }
}