package com.example.demo.service;

import com.example.demo.dto.FriendItem;
import com.example.demo.dto.FriendRequestItem;

import java.util.List;
import java.util.Set;

public interface FriendService {
    Set<Long> getFriendIds(Long userId);

    // 发送好友申请
    void sendRequest(Long userId, Long targetUserId);
    // 同意申请
    void acceptRequest(Long userId, Long requestUserId);
    // 拒绝申请
    void rejectRequest(Long userId, Long requestUserId);
    // 删除好友
    void removeFriend(Long userId, Long friendId);
    // 判断是否好友（status=1）
    boolean isFriend(Long userIdA, Long userIdB);
    // 好友列表
    List<FriendItem> getFriendList(Long userId);
    // 收到的申请列表
    List<FriendRequestItem> getPendingRequests(Long userId);

    // 用于搜索时判断关系状态：NOT_FRIEND / PENDING / FRIEND
    String getFriendStatus(Long currentUserId, Long targetUserId);
}