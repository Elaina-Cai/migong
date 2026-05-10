package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.ChatMessage;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public void save(Long fromUserId, Long toUserId, String content, int status) {
        ChatMessage msg = new ChatMessage();
        msg.setFromUserId(fromUserId);
        msg.setToUserId(toUserId);
        msg.setContent(content);
        msg.setChatMessageStatus(status);
        chatMessageMapper.insert(msg);
    }

    @Transactional
    @Override
    public List<ChatMessage> fetchAndMarkReceived(Long userId) {
        // 查询未接收消息
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getToUserId, userId)
                .eq(ChatMessage::getChatMessageStatus, 0)
                .orderByAsc(ChatMessage::getCreatedAt);
        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

        // 批量标记为已接收
        if (!messages.isEmpty()) {
            LambdaUpdateWrapper<ChatMessage> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ChatMessage::getToUserId, userId)
                    .eq(ChatMessage::getChatMessageStatus, 0)
                    .set(ChatMessage::getChatMessageStatus, 1);
            chatMessageMapper.update(null, updateWrapper);
        }
        return messages;
    }

    @Override
    public List<ChatMessage> getHistory(Long userIdA, Long userIdB) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                        .eq(ChatMessage::getFromUserId, userIdA).eq(ChatMessage::getToUserId, userIdB)
                        .or()
                        .eq(ChatMessage::getFromUserId, userIdB).eq(ChatMessage::getToUserId, userIdA)
                )
                .orderByAsc(ChatMessage::getCreatedAt)
                .last("LIMIT 100");  // 限制最近100条
        return chatMessageMapper.selectList(wrapper);
    }
}