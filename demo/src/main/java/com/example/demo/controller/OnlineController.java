package com.example.demo.controller;

import com.example.demo.utils.Result;
import com.example.demo.websocket.MazeWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnlineController {

    @GetMapping("/online-count")
    public Result<Integer> onlineCount() {
        return Result.success(MazeWebSocketHandler.getOnlineCount());
    }
}