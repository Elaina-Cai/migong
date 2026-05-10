package com.example.demo.controller;

import com.example.demo.dto.FriendItem;
import com.example.demo.dto.FriendRequestItem;
import com.example.demo.service.FriendService;
import com.example.demo.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // 发送申请
    @PostMapping("/request")
    public Result<?> sendRequest(HttpServletRequest request, @RequestBody Map<String, Long> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long targetUserId = body.get("targetUserId");
        friendService.sendRequest(userId, targetUserId);
        return Result.success("已发送好友申请");
    }

    // 同意申请
    @PostMapping("/accept")
    public Result<?> acceptRequest(HttpServletRequest request, @RequestBody Map<String, Long> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long requestUserId = body.get("requestUserId");
        friendService.acceptRequest(userId, requestUserId);
        return Result.success();
    }

    // 拒绝申请
    @PostMapping("/reject")
    public Result<?> rejectRequest(HttpServletRequest request, @RequestBody Map<String, Long> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long requestUserId = body.get("requestUserId");
        friendService.rejectRequest(userId, requestUserId);
        return Result.success();
    }

    // 获取收到的申请列表
    @GetMapping("/requests")
    public Result<List<FriendRequestItem>> pendingRequests(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(friendService.getPendingRequests(userId));
    }

    // 好友列表
    @GetMapping("/list")
    public Result<List<FriendItem>> friendList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(friendService.getFriendList(userId));
    }

    // 删除好友
    @DeleteMapping("/remove/{friendUserId}")
    public Result<?> removeFriend(HttpServletRequest request, @PathVariable Long friendUserId) {
        Long userId = (Long) request.getAttribute("userId");
        friendService.removeFriend(userId, friendUserId);
        return Result.success();
    }
}