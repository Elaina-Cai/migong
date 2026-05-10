package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.dto.FriendItem;
import com.example.demo.dto.FriendRequestItem;
import com.example.demo.entity.Friend;
import com.example.demo.entity.OnlineUser;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.FriendMapper;
import com.example.demo.mapper.OnlineUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.FriendService;
import com.example.demo.websocket.MazeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendMapper friendMapper;
    private final UserMapper userMapper;
    private final OnlineUserMapper onlineUserMapper;

    @Override
    public Set<Long> getFriendIds(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getStatus, 1);   // 只查已通过的好友
        return friendMapper.selectList(wrapper).stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toSet());
    }

    @Transactional
    @Override
    public void sendRequest(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(400, "不能添加自己为好友");
        }
        String currentStatus = getFriendStatus(userId, targetUserId);
        if ("FRIEND".equals(currentStatus)) {
            throw new BusinessException(400, "已经是好友");
        }
        if ("PENDING".equals(currentStatus)) {
            throw new BusinessException(400, "已有待处理的申请");
        }

        // 处理可能存在 status=0 的记录（之前拒绝/删除过）
        upsertFriendStatus(userId, targetUserId, 2);
        upsertFriendStatus(targetUserId, userId, 2);
    }
    /**
     * 更新已存在记录的 status，若不存在则插入新记录
     */
    private void upsertFriendStatus(Long userId, Long friendId, int status) {
        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .set(Friend::getStatus, status);
        int affected = friendMapper.update(null, updateWrapper);
        if (affected == 0) {
            Friend f = new Friend();
            f.setUserId(userId);
            f.setFriendId(friendId);
            f.setStatus(status);
            friendMapper.insert(f);
        }
    }

    @Transactional
    @Override
    public void acceptRequest(Long userId, Long requestUserId) {
        // 找到对方发给我的申请记录（user_id=requestUserId, friend_id=userId, status=2）
        LambdaUpdateWrapper<Friend> wrapper1 = new LambdaUpdateWrapper<>();
        wrapper1.eq(Friend::getUserId, requestUserId)
                .eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 2)
                .set(Friend::getStatus, 1);
        int updated1 = friendMapper.update(null, wrapper1);

        LambdaUpdateWrapper<Friend> wrapper2 = new LambdaUpdateWrapper<>();
        wrapper2.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, requestUserId)
                .eq(Friend::getStatus, 2)
                .set(Friend::getStatus, 1);
        int updated2 = friendMapper.update(null, wrapper2);

        if (updated1 == 0 || updated2 == 0) {
            throw new BusinessException(404, "未找到对应的好友申请");
        }
    }

    @Transactional
    @Override
    public void rejectRequest(Long userId, Long requestUserId) {
        // 将 status=2 改为 0
        LambdaUpdateWrapper<Friend> wrapper1 = new LambdaUpdateWrapper<>();
        wrapper1.eq(Friend::getUserId, requestUserId)
                .eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 2)
                .set(Friend::getStatus, 0);
        friendMapper.update(null, wrapper1);

        LambdaUpdateWrapper<Friend> wrapper2 = new LambdaUpdateWrapper<>();
        wrapper2.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, requestUserId)
                .eq(Friend::getStatus, 2)
                .set(Friend::getStatus, 0);
        friendMapper.update(null, wrapper2);
    }

    @Transactional
    @Override
    public void removeFriend(Long userId, Long friendId) {
        LambdaUpdateWrapper<Friend> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .eq(Friend::getStatus, 1)   // 只删除已通过的
                .set(Friend::getStatus, 0);
        friendMapper.update(null, wrapper);

        LambdaUpdateWrapper<Friend> wrapper2 = new LambdaUpdateWrapper<>();
        wrapper2.eq(Friend::getUserId, friendId)
                .eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 1)
                .set(Friend::getStatus, 0);
        friendMapper.update(null, wrapper2);
    }

    @Override
    public boolean isFriend(Long userIdA, Long userIdB) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userIdA)
                .eq(Friend::getFriendId, userIdB)
                .eq(Friend::getStatus, 1);
        return friendMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<FriendItem> getFriendList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getStatus, 1);
        List<Friend> relations = friendMapper.selectList(wrapper);
        List<Long> friendIds = relations.stream().map(Friend::getFriendId).collect(Collectors.toList());
        if (friendIds.isEmpty()) return Collections.emptyList();

        // 批量查询好友信息
        List<User> users = userMapper.selectBatchIds(friendIds);
        Map<Long, String> usernameMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername));

        // 从 online_users 表获取在线用户 ID 集合
        Set<Long> onlineUserIdSet = onlineUserMapper.selectList(null).stream()
                .map(OnlineUser::getUserId)
                .collect(Collectors.toSet());

        // 构造 FriendItem 列表
        List<FriendItem> items = friendIds.stream().map(fid -> {
            FriendItem item = new FriendItem();
            item.setUserId(fid);
            item.setUsername(usernameMap.getOrDefault(fid, "未知"));
            item.setOnline(onlineUserIdSet.contains(fid));
            return item;
        }).collect(Collectors.toList());

        // 在线优先排序
        items.sort(Comparator.comparing(FriendItem::isOnline).reversed());

        return items;
    }

    @Override
    public List<FriendRequestItem> getPendingRequests(Long userId) {
        // 查询别人发给我、status=2 的申请
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getFriendId, userId)
                .eq(Friend::getStatus, 2);
        List<Friend> requests = friendMapper.selectList(wrapper);
        List<Long> requesterIds = requests.stream().map(Friend::getUserId).collect(Collectors.toList());
        if (requesterIds.isEmpty()) return Collections.emptyList();

        List<User> users = userMapper.selectBatchIds(requesterIds);
        Map<Long, String> usernameMap = users.stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername));

        return requests.stream().map(r -> {
            FriendRequestItem item = new FriendRequestItem();
            item.setUserId(r.getUserId());
            item.setUsername(usernameMap.getOrDefault(r.getUserId(), "未知"));
            item.setCreatedAt(r.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public String getFriendStatus(Long currentUserId, Long targetUserId) {
        // 先查已通过
        LambdaQueryWrapper<Friend> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(Friend::getUserId, currentUserId)
                .eq(Friend::getFriendId, targetUserId)
                .eq(Friend::getStatus, 1);
        if (friendMapper.selectCount(wrapper1) > 0) return "FRIEND";

        // 再查是否有 pending 记录（无论谁申请的）
        LambdaQueryWrapper<Friend> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(Friend::getUserId, currentUserId)
                .eq(Friend::getFriendId, targetUserId)
                .eq(Friend::getStatus, 2);
        if (friendMapper.selectCount(wrapper2) > 0) return "PENDING";

        wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(Friend::getUserId, targetUserId)
                .eq(Friend::getFriendId, currentUserId)
                .eq(Friend::getStatus, 2);
        if (friendMapper.selectCount(wrapper2) > 0) return "PENDING";

        return "NOT_FRIEND";
    }
}