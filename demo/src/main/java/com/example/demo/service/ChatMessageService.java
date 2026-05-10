package com.example.demo.service;

import com.example.demo.entity.ChatMessage;
import java.util.List;

public interface ChatMessageService {
    // 保存消息（status 由调用方决定）
    void save(Long fromUserId, Long toUserId, String content, int status);

    // 获取某用户所有未接收的消息，并批量更新为已接收
    List<ChatMessage> fetchAndMarkReceived(Long userId);

    // 获取两个用户之间的聊天记录
    List<ChatMessage> getHistory(Long userIdA, Long userIdB);
}