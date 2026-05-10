package com.example.demo.controller;

import com.example.demo.entity.ChatMessage;
import com.example.demo.service.ChatMessageService;
import com.example.demo.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/history")
    public Result<List<ChatMessage>> getHistory(
            HttpServletRequest request,
            @RequestParam Long friendUserId) {
        Long currentUserId = (Long) request.getAttribute("userId");
        return Result.success(chatMessageService.getHistory(currentUserId, friendUserId));
    }
}