package com.example.demo.websocket;

import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GameRoom {
    private String roomId;
    private String hostUserId;
    private Set<String> players = ConcurrentHashMap.newKeySet();
    private Set<String> readyPlayers = ConcurrentHashMap.newKeySet();
    private int[][] grid;
    private int endRow, endCol;
    private boolean started = false;
    private long startTime;   // 游戏开始时间戳（毫秒）
    private int rows;
    private int cols;
    private String algorithm;

    private Map<String, int[]> positions = new ConcurrentHashMap<>(); // userId -> [row, col]
    public Map<String, int[]> getPositions() { return positions; }
    public boolean isAllReady() {
        return readyPlayers.size() == players.size();
    }
}