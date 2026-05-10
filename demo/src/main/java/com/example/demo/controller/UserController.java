package com.example.demo.controller;

import com.example.demo.dto.UserSearchResult;
import com.example.demo.service.UserService;
import com.example.demo.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/search")
    public Result<List<UserSearchResult>> searchUsers(
            HttpServletRequest request,
            @RequestParam String keyword) {
        Long currentUserId = (Long) request.getAttribute("userId");
        List<UserSearchResult> results = userService.searchByUsername(keyword, currentUserId);
        return Result.success(results);
    }
}