package com.example.demo.websocket;

import com.example.demo.mapper.OnlineUserMapper;
import com.example.demo.service.MultiGameService;
import com.example.demo.utils.JwtUtil;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 这是websocket服务端 功能是：监听websocket地址/ws/maze的所有消息，包括玩家移动、创建连接、断开连接
 * 具体地址（/ws/maze）是写在websocket配置类那里，在添加websocket服务端的时候写上这个添加的websocket服务端监听的地址
 */
@Component
public class MazeWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final RoomService roomService;
    private final MultiGameService multiGameService;
    private final OnlineUserMapper onlineUserMapper;

    // userId -> 该用户的所有活跃连接（支持同一账号多窗口）
    private static final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    // 连接计数器，userId -> 连接数
    private static final ConcurrentHashMap<String, Integer> connectionCount = new ConcurrentHashMap<>();

    public MazeWebSocketHandler(JwtUtil jwtUtil, RoomService roomService,
                                MultiGameService multiGameService,
                                OnlineUserMapper onlineUserMapper) {
        this.jwtUtil = jwtUtil;
        this.roomService = roomService;
        this.multiGameService = multiGameService;
        this.onlineUserMapper = onlineUserMapper;
    }

    /**
     * 建立连接，并且保存会话（保存session）
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        String token = null;
        if (uri.contains("?token=")) {
            token = uri.substring(uri.indexOf("?token=") + 7);
            if (token.contains("&")) token = token.substring(0, token.indexOf("&"));
        }
        if (token == null || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        String userIdStr = userId.toString();
        //获取 userId 和 userIdStr 后：
        String username = jwtUtil.getUsernameFromToken(token);  // 从 token 中获取用户名
        session.getAttributes().put("username", username);
        // 更新连接计数
        int count = connectionCount.merge(userIdStr, 1, Integer::sum);
        if (count == 1) {
            // 第一次上线，写入 online_users 表
            onlineUserMapper.upsertOnline(userId, username);
        }
        // 将新连接加入该用户的连接集合
        userSessions.computeIfAbsent(userIdStr, k -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("userId", userIdStr);
        System.out.println("用户 " + userIdStr + " 上线，当前在线用户数：" + userSessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        JSONObject msg;
        try {
            msg = new JSONObject(message.getPayload());
        } catch (Exception e) {
            sendMessage(session, "error", "消息格式错误");
            return;
        }

        String type = msg.optString("type");
        JSONObject data = msg.optJSONObject("data");

        try {
            switch (type) {
                case "create": handleCreate(userId, data, session); break;
                case "join":   handleJoin(userId, data, session); break;
                case "start":  handleStart(userId, data); break;
                case "move":   handleMove(userId, data); break;
                case "ready":  handleReady(userId, data); break;
                case "unready":handleUnready(userId, data); break;
                case "leave":  handleLeave(userId, data, session); break;
                case "kick":   handleKick(userId, data, session); break;
                default: sendMessage(session, "error", "未知消息类型: " + type);
            }
        } catch (Exception e) {
            sendMessage(session, "error", e.getMessage());
        }
    }

    /**
     * 断开连接，删除会话(删除session)
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            // 从该用户的连接集合中移除当前 session
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            // 更新连接计数
            int count = connectionCount.compute(userId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
            if (count == 0) {
                // 所有连接都断开，从 online_users 表中删除
                onlineUserMapper.deleteById(Long.parseLong(userId));
            }

            String roomId = (String) session.getAttributes().get("roomId");
            if (roomId != null) {
                try {
                    boolean wasHost = userId.equals(roomService.getRoom(roomId).getHostUserId());
                    roomService.leaveRoom(roomId, userId);
                    JSONObject leaveData = new JSONObject();
                    leaveData.put("userId", userId);
                    leaveData.put("reason", "connection_lost");
                    if (wasHost) {
                        leaveData.put("newHost", roomService.getRoom(roomId).getHostUserId());
                    }
                    broadcastToRoom(roomId, "player_left", leaveData);
                } catch (Exception ignored) {}
            }
            System.out.println("用户 " + userId + " 下线，当前在线用户数：" + userSessions.size());
        }
    }

    // ========== 辅助方法 ==========
    private void sendMessage(WebSocketSession session, String type, Object data) throws IOException {
        JSONObject msg = new JSONObject();
        msg.put("type", type);
        msg.put("data", data == null ? JSONObject.NULL : data);
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(msg.toString()));
            }
        }
    }

    private void broadcastToRoom(String roomId, String type, Object data) throws IOException {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;
        for (String uid : room.getPlayers()) {
            // 向该用户的所有连接发送消息
            Set<WebSocketSession> sessions = userSessions.get(uid);
            if (sessions != null) {
                for (WebSocketSession s : sessions) {
                    if (s.isOpen()) {
                        sendMessage(s, type, data);
                    }
                }
            }
        }
    }

    // ========== 业务处理 ==========
    private void handleCreate(String userId, JSONObject data, WebSocketSession session) throws Exception {
        int rows = data.optInt("rows", 21);
        int cols = data.optInt("cols", 21);
        String algorithm = data.optString("algorithm", "dfs");
        GameRoom room = roomService.createRoom(userId, rows, cols, algorithm);
        session.getAttributes().put("roomId", room.getRoomId());

        JSONObject info = roomInfoToJson(room);
        sendMessage(session, "room_info", info);
    }

    private void handleJoin(String userId, JSONObject data, WebSocketSession session) throws Exception {
        String roomId = data.getString("roomId");
        // 如果已在其他房间，先退出
        String oldRoomId = (String) session.getAttributes().get("roomId");
        if (oldRoomId != null && !oldRoomId.equals(roomId)) {
            roomService.leaveRoom(oldRoomId, userId);
            broadcastToRoom(oldRoomId, "player_left", new JSONObject().put("userId", userId));
        }
        GameRoom room = roomService.joinRoom(roomId, userId);
        session.getAttributes().put("roomId", roomId);

        broadcastToRoom(roomId, "player_joined", new JSONObject().put("userId", userId));
        sendMessage(session, "room_info", roomInfoToJson(room));
    }

    private void handleStart(String userId, JSONObject data) throws Exception {
        String roomId = data.getString("roomId");
        roomService.startGame(roomId, userId);

        // 广播游戏开始，并附带新迷宫数据
        GameRoom room = roomService.getRoom(roomId);
        JSONObject startData = new JSONObject();
        startData.put("message", "游戏开始！");
        startData.put("grid", room.getGrid());
        startData.put("endRow", room.getEndRow());
        startData.put("endCol", room.getEndCol());
        startData.put("positions", room.getPositions());
        broadcastToRoom(roomId, "game_started", startData);
    }

    private void handleMove(String userId, JSONObject data) throws Exception {
        String roomId = data.getString("roomId");
        String direction = data.getString("direction");
        int[] newPos = roomService.movePlayer(roomId, userId, direction);
        GameRoom room = roomService.getRoom(roomId);
        boolean won = (newPos[0] == room.getEndRow() && newPos[1] == room.getEndCol());

        JSONObject moveData = new JSONObject();
        moveData.put("userId", userId);
        moveData.put("row", newPos[0]);
        moveData.put("col", newPos[1]);

        if (won) {
            long elapsed = (System.currentTimeMillis() - room.getStartTime()) / 1000;
            moveData.put("elapsedSeconds", elapsed);

            // 记录战绩
            int playerCount = room.getPlayers().size();
            multiGameService.recordWin(roomId, userId, (int) elapsed, playerCount);

            broadcastToRoom(roomId, "winner", moveData);
        } else {
            broadcastToRoom(roomId, "player_moved", moveData);
        }
    }
    //准备
    private void handleReady(String userId, JSONObject data) throws Exception {
        String roomId = data.getString("roomId");
        roomService.readyPlayer(roomId, userId);
        JSONObject rd = new JSONObject();
        rd.put("userId", userId);
        rd.put("ready", true);
        broadcastToRoom(roomId, "player_ready", rd);
    }
    //取消准备
    private void handleUnready(String userId, JSONObject data) throws Exception {
        String roomId = data.getString("roomId");
        roomService.unreadyPlayer(roomId, userId);
        JSONObject rd = new JSONObject();
        rd.put("userId", userId);
        rd.put("ready", false);
        broadcastToRoom(roomId, "player_ready", rd);
    }
    //离开
    private void handleLeave(String userId, JSONObject data, WebSocketSession session) throws Exception {
        String roomId = data.getString("roomId");
        boolean wasHost = userId.equals(roomService.getRoom(roomId).getHostUserId());
        roomService.leaveRoom(roomId, userId);
        session.getAttributes().remove("roomId");

        JSONObject leaveData = new JSONObject();
        leaveData.put("userId", userId);
        if (wasHost) {
            GameRoom room = roomService.getRoom(roomId);
            if (room != null) leaveData.put("newHost", room.getHostUserId());
        }
        broadcastToRoom(roomId, "player_left", leaveData);
        sendMessage(session, "left", new JSONObject());
    }
    //踢出
    private void handleKick(String userId, JSONObject data, WebSocketSession session) throws Exception {
        String roomId = data.getString("roomId");
        String targetId = data.getString("targetUserId");
        roomService.kickPlayer(roomId, userId, targetId);

        // 踢掉被踢用户的所有连接
        Set<WebSocketSession> targetSessions = userSessions.get(targetId);
        if (targetSessions != null) {
            for (WebSocketSession ts : targetSessions) {
                ts.getAttributes().remove("roomId");
                sendMessage(ts, "kicked", new JSONObject().put("message", "你已被房主移出房间"));
            }
        }

        JSONObject leaveData = new JSONObject();
        leaveData.put("userId", targetId);
        leaveData.put("reason", "kicked");
        broadcastToRoom(roomId, "player_left", leaveData);
    }

    // ========== 工具 ==========
    private JSONObject roomInfoToJson(GameRoom room) {
        JSONObject info = new JSONObject();
        info.put("roomId", room.getRoomId());
        info.put("host", room.getHostUserId());
        info.put("players", room.getPlayers());
        info.put("readyPlayers", room.getReadyPlayers());
        info.put("grid", room.getGrid());
        info.put("endRow", room.getEndRow());
        info.put("endCol", room.getEndCol());
        info.put("started", room.isStarted());
        //所有玩家的当前位置
        info.put("positions", room.getPositions()); // Map<String, int[]>
        return info;
    }

    public static int getOnlineCount() {
        int count = 0;
        for (Set<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) count++;
            }
        }
        return count;
    }

    /**
     * 获取当前所有在线用户的 ID 集合
     */
    public static Set<String> getOnlineUserIds() {
        Set<String> onlineIds = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, Set<WebSocketSession>> entry : userSessions.entrySet()) {
            for (WebSocketSession s : entry.getValue()) {
                if (s.isOpen()) {
                    onlineIds.add(entry.getKey());
                    break; // 只要该用户有一个打开的session就算在线
                }
            }
        }
        return onlineIds;
    }
}