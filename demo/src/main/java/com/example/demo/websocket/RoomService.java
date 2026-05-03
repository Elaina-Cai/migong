package com.example.demo.websocket;

import com.example.demo.utils.MazeGenerator;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static final int MAX_ROOMS = 50;

    public GameRoom createRoom(String userId, int rows, int cols, String algorithm) {
        if (rooms.size() >= MAX_ROOMS) {
            throw new RuntimeException("服务器房间已满，请稍后再试");
        }
        String roomId = String.format("%06d", new Random().nextInt(999999));
        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setHostUserId(userId);
        room.getPlayers().add(userId);
        // 生成迷宫
        int[][] grid = MazeGenerator.generate(rows, cols, algorithm);
        grid[1][0] = 0;                              // 入口
        grid[grid.length-2][grid[0].length-1] = 0;   // 出口
        room.setGrid(grid);
        room.setEndRow(grid.length-2);
        room.setEndCol(grid[0].length-1);
        room.getPositions().put(userId, new int[]{1, 0});
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom joinRoom(String roomId, String userId) {
        // 检查是否已在其他房间
        for (Map.Entry<String, GameRoom> entry : rooms.entrySet()) {
            if (entry.getValue().getPlayers().contains(userId)) {
                throw new RuntimeException("你已经在一个房间中，请先退出当前房间");
            }
        }
        GameRoom room = rooms.get(roomId);
        if (room == null || room.isStarted()) throw new RuntimeException("房间不存在或已开始");
        room.getPlayers().add(userId);
        room.getPositions().put(userId, new int[]{1, 0});
        return room;
    }

    public void startGame(String roomId, String userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) throw new RuntimeException("房间不存在");
        if (!room.getHostUserId().equals(userId)) throw new RuntimeException("只有房主才能开始游戏");
        if (!room.isAllReady()) throw new RuntimeException("还有玩家未准备");
        room.setStarted(true);
    }

    public int[] movePlayer(String roomId, String userId, String direction) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !room.isStarted()) throw new RuntimeException("游戏未开始");
        int[] pos = room.getPositions().get(userId);
        if (pos == null) throw new RuntimeException("你不在该房间");
        int[][] grid = room.getGrid();
        int newRow = pos[0], newCol = pos[1];
        switch (direction) {
            case "up": newRow--; break;
            case "down": newRow++; break;
            case "left": newCol--; break;
            case "right": newCol++; break;
            default: throw new RuntimeException("无效方向");
        }
        if (newRow < 0 || newRow >= grid.length ||
                newCol < 0 || newCol >= grid[0].length ||
                grid[newRow][newCol] == 1) {
            throw new RuntimeException("撞墙了");
        }
        pos[0] = newRow;
        pos[1] = newCol;
        return pos;
    }

    public void readyPlayer(String roomId, String userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !room.getPlayers().contains(userId))
            throw new RuntimeException("你不在该房间中");
        room.getReadyPlayers().add(userId);
    }

    public void unreadyPlayer(String roomId, String userId) {
        GameRoom room = rooms.get(roomId);
        if (room != null) room.getReadyPlayers().remove(userId);
    }

    public void leaveRoom(String roomId, String userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        room.getPlayers().remove(userId);
        room.getPositions().remove(userId);
        room.getReadyPlayers().remove(userId);

        if (room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
            return;
        }
        // 如果房主离开，转移给第一个剩余玩家
        if (userId.equals(room.getHostUserId())) {
            String newHost = room.getPlayers().iterator().next();
            room.setHostUserId(newHost);
        }
    }

    public void kickPlayer(String roomId, String hostUserId, String targetUserId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) throw new RuntimeException("房间不存在");
        if (!hostUserId.equals(room.getHostUserId())) throw new RuntimeException("只有房主才能踢人");
        if (hostUserId.equals(targetUserId)) throw new RuntimeException("不能踢自己");
        if (!room.getPlayers().contains(targetUserId)) throw new RuntimeException("该玩家不在房间中");

        room.getPlayers().remove(targetUserId);
        room.getPositions().remove(targetUserId);
        room.getReadyPlayers().remove(targetUserId);
        if (room.getPlayers().isEmpty()) rooms.remove(roomId);
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }
}