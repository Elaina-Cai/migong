package com.example.demo.websocket;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.User;
import com.example.demo.mapper.OnlineUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.ChatMessageService;
import com.example.demo.service.FriendService;
import com.example.demo.service.MultiGameService;
import com.example.demo.utils.JwtUtil;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MazeWebSocketHandler 是整个多人游戏和即时通讯的 WebSocket 消息总入口。它同时承担了以下几个核心职责：
 * 1. 用户连接管理（在线状态）
 * 每个 WebSocket 连接建立时，通过 URL 参数中的 JWT 鉴权，拿到 userId 和 username。
 * 将连接放入 userSessions（一个用户可以有多个窗口，对应多个连接）。
 * 使用 connectionCount 计数器判断用户是否首次上线：
 * 如果是第一次在线 → 向 online_users 表插入记录（标记在线）。
 * 连接关闭时：
 * 从 userSessions 移除该连接。
 * 递减计数器，当计数器归零时删除 online_users 表中的在线记录（标记离线）。
 * 2. 多人迷宫游戏房间
 * 路由所有游戏相关消息：create、join、start、move、ready、unready、leave、kick。
 * 委托给 RoomService 和 MultiGameService 处理房间创建、加入、开始、移动、胜负判定等逻辑。
 * 将游戏状态变化（玩家加入、移动、胜负等）广播给同一房间的所有在线玩家。
 * 3. 好友聊天（私聊）
 * 处理 chat_send 消息类型。
 * 校验双方是否为好友（调用 FriendService.isFriend）。
 * 将消息存入 chat_message 表（无论对方在线与否，记录所有消息）。
 * 对方在线 → 状态存为 1（已接收），并立即通过 WebSocket 推送 chat_receive 消息给对方。
 * 对方离线 → 状态存为 0（未接收），等待对方上线后推送。
 * 4. 离线聊天消息补推
 * 在 afterConnectionEstablished 中，用户连接成功后，检查 chat_message 表中所有发给该用户且状态为 0 的消息。
 * 逐条推送给用户，并将状态更新为 1（已接收），确保用户不会错过离线期间的消息。
 * 5. 辅助功能
 * 提供 getOnlineCount() 静态方法，统计当前活跃连接数（用于在线人数展示）。
 * 提供 getOnlineUserIds() 静态方法，返回在线用户 ID 集合（可用于好友列表在线状态，但最近版本已改用数据库 online_users 表）。
 * 提供 sendMessage 和 broadcastToRoom 工具方法，封装消息的 JSON 序列化和多连接发送。
 * 总结
 * 这个类是一个 集多人游戏实时交互 + 好友私聊 + 在线状态同步 + 离线消息缓存 于一体的 WebSocket 核心处理器。它复用同一个连接实现了多种业务功能，避免了重复鉴权和管理多套 WebSocket 连接，设计紧凑且功能边界清晰。
 */
@Component
public class MazeWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final RoomService roomService;
    private final MultiGameService multiGameService;
    private final OnlineUserMapper onlineUserMapper;
    private final ChatMessageService chatMessageService;
    private final FriendService friendService;   // 用于校验好友关系
    private final UserMapper userMapper;

    // userId -> 该用户的所有活跃连接（支持同一账号多窗口）
    private static final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    // 连接计数器，userId -> 连接数
    private static final ConcurrentHashMap<String, Integer> connectionCount = new ConcurrentHashMap<>();

    public MazeWebSocketHandler(JwtUtil jwtUtil, RoomService roomService,
                                MultiGameService multiGameService,
                                OnlineUserMapper onlineUserMapper,
                                ChatMessageService chatMessageService,
                                FriendService friendService,
                                UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.roomService = roomService;
        this.multiGameService = multiGameService;
        this.onlineUserMapper = onlineUserMapper;
        this.chatMessageService = chatMessageService;
        this.friendService = friendService;
        this.userMapper = userMapper;
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
        // 拉取并推送离线消息
        List<ChatMessage> offlineMsgs = chatMessageService.fetchAndMarkReceived(userId);
        if (!offlineMsgs.isEmpty()) {
            for (ChatMessage msg : offlineMsgs) {
                // 从数据库查询发送者用户名
                User sender = userMapper.selectById(msg.getFromUserId());
                String senderUsername = (sender != null) ? sender.getUsername() : "未知用户";

                JSONObject chatMsg = new JSONObject();
                chatMsg.put("fromUserId", msg.getFromUserId().toString());
                chatMsg.put("fromUsername", senderUsername);
                chatMsg.put("content", msg.getContent());
                chatMsg.put("timestamp", msg.getCreatedAt().toString());
                sendMessage(session, "chat_receive", chatMsg);
            }
        }
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
                case "chat_send": handleChatSend(userId, data, session); break;
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
            Integer count = connectionCount.compute(userId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
            if (count == null) {
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

    /**
     * 全量存储 + 在线推送
     * @param fromUserId
     * @param data
     * @param session
     * @throws Exception
     */
    private void handleChatSend(String fromUserId, JSONObject data, WebSocketSession session) throws Exception {
        String toUserId = data.getString("toUserId");
        String content = data.getString("content");

        // 校验好友关系
        if (!friendService.isFriend(Long.parseLong(fromUserId), Long.parseLong(toUserId))) {
            sendMessage(session, "error", "你们还不是好友，无法发送消息");
            return;
        }

        // 判断接收方是否在线
        boolean online = userSessions.containsKey(toUserId) &&
                userSessions.get(toUserId).stream().anyMatch(WebSocketSession::isOpen);

        // 存入数据库，状态根据在线决定
        int status = online ? 1 : 0;
        chatMessageService.save(Long.parseLong(fromUserId), Long.parseLong(toUserId), content, status);

        // 如果在线，立即推送
        if (online) {
            String fromUsername = (String) session.getAttributes().get("username");
            JSONObject chatMsg = new JSONObject();
            chatMsg.put("fromUserId", fromUserId);
            chatMsg.put("fromUsername", fromUsername);
            chatMsg.put("content", content);
            chatMsg.put("timestamp", System.currentTimeMillis());

            Set<WebSocketSession> targetSessions = userSessions.get(toUserId);
            for (WebSocketSession s : targetSessions) {
                if (s.isOpen()) {
                    sendMessage(s, "chat_receive", chatMsg);
                }
            }
        }
    }
}