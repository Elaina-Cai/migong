package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dto.MultiLeaderboardItem;
import com.example.demo.dto.MultiLeaderboardResponse;
import com.example.demo.entity.MultiGameRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.MultiGameRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.MultiGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MultiGameServiceImpl implements MultiGameService {

    private final MultiGameRecordMapper recordMapper;
    private final UserMapper userMapper;

    @Override
    public void recordWin(String roomId, String winnerUserIdStr, int elapsedSeconds, int playerCount) {
        MultiGameRecord record = new MultiGameRecord();
        record.setUserId(Long.parseLong(winnerUserIdStr));
        record.setRoomId(roomId);
        record.setElapsedSeconds(elapsedSeconds);
        record.setPlayerCount(playerCount);
        record.setPlayedAt(java.time.LocalDateTime.now());
        recordMapper.insert(record);
    }

    @Override
    public MultiLeaderboardResponse getMultiLeaderboard(Long currentUserId) {
        // 统计每个用户的获胜次数和最快用时
        // 使用 group by 查询
        List<Map<String, Object>> stats = recordMapper.selectMaps(
                new LambdaQueryWrapper<MultiGameRecord>()
                        .select(
                                MultiGameRecord::getUserId,
                                MultiGameRecord::getElapsedSeconds
                        )
        );

        // 手动统计：userId -> {wins, fastest}
        Map<Long, Integer> winCount = new HashMap<>();
        Map<Long, Integer> fastestTime = new HashMap<>();

        for (Map<String, Object> row : stats) {
            Long uid = (Long) row.get("user_id");
            Integer time = (Integer) row.get("elapsed_seconds");
            winCount.merge(uid, 1, Integer::sum);
            fastestTime.merge(uid, time, Math::min);
        }

        // 构建排行榜列表
        List<MultiLeaderboardItem> topList = new ArrayList<>();
        List<Long> userIds = new ArrayList<>(winCount.keySet());
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            Map<Long, String> usernameMap = users.stream()
                    .collect(Collectors.toMap(User::getUserId, User::getUsername));

            for (Long uid : winCount.keySet()) {
                MultiLeaderboardItem item = new MultiLeaderboardItem();
                item.setUserId(uid);
                item.setUsername(usernameMap.getOrDefault(uid, "未知用户"));
                item.setWins(winCount.get(uid));
                item.setFastestTime(fastestTime.get(uid)); // 注意这里用 int，没有记录的可为0，但必须存在
                topList.add(item);
            }
        }

        // 按胜场降序，胜场相同按最快用时升序
        topList.sort(Comparator
                .comparingInt(MultiLeaderboardItem::getWins).reversed()
                .thenComparingInt(MultiLeaderboardItem::getFastestTime));

        // 设置排名
        for (int i = 0; i < topList.size(); i++) {
            topList.get(i).setRank(i + 1);
        }

        // 截取前50
        if (topList.size() > 50) {
            topList = topList.subList(0, 50);
        }

        // 查询当前用户排名
        MultiLeaderboardItem myRank = null;
        if (currentUserId != null && winCount.containsKey(currentUserId)) {
            myRank = topList.stream()
                    .filter(item -> item.getUserId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);
        }

        MultiLeaderboardResponse response = new MultiLeaderboardResponse();
        response.setTopList(topList);
        response.setMyRank(myRank);
        return response;
    }
}