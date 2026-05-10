package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.UserSearchResult;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.FriendService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FriendService friendService;

    @Override
    public List<UserSearchResult> searchByUsername(String keyword, Long currentUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(User::getUsername, keyword)
                .ne(User::getUserId, currentUserId)
                .last("LIMIT 20");
        List<User> users = userMapper.selectList(wrapper);

        return users.stream().map(u -> {
            UserSearchResult r = new UserSearchResult();
            r.setUserId(u.getUserId());
            r.setUsername(u.getUsername());
            r.setFriendStatus(friendService.getFriendStatus(currentUserId, u.getUserId()));
            return r;
        }).collect(Collectors.toList());
    }
}