// ========== 配置 ==========
const API_BASE = '';                // 同源请求
const TOKEN_KEY = 'maze_token';
const USERNAME_KEY = 'maze_username';
const USERID_KEY = 'maze_userid';

// ========== 工具函数 ==========
function setUserId(id) { localStorage.setItem(USERID_KEY, id); }
function getUserId() { return localStorage.getItem(USERID_KEY); }
function removeUserId() { localStorage.removeItem(USERID_KEY); }
function getToken() { return localStorage.getItem(TOKEN_KEY); }
function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
function removeToken() { localStorage.removeItem(TOKEN_KEY); }
function setUsername(u) { localStorage.setItem(USERNAME_KEY, u); }
function getUsername() { return localStorage.getItem(USERNAME_KEY); }
function removeUsername() { localStorage.removeItem(USERNAME_KEY); }

async function request(url, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers,
    };
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(API_BASE + url, { ...options, headers });
    let data;
    try { data = await res.json(); } catch (e) { throw new Error('服务器响应异常'); }
    if (data.code !== 200) throw new Error(data.message || '请求失败');
    return data;
}

// ========== DOM 元素 ==========
const loginPage = document.getElementById('login-page');
const gameHall = document.getElementById('game-hall');
const authForm = document.getElementById('auth-form');
const authBtn = document.getElementById('auth-btn');
const toggleLink = document.getElementById('toggle-link');
const errorMsg = document.getElementById('error-msg');
const displayUsername = document.getElementById('display-username');
const heroUsername = document.getElementById('hero-username');
const logoutBtn = document.getElementById('logout-btn');
const navItems = document.querySelectorAll('.nav-item');
const pages = document.querySelectorAll('.page');

let isLoginMode = true;   // true=登录，false=注册

// ========== 切换登录/注册 ==========
function toggleMode() {
    isLoginMode = !isLoginMode;
    authBtn.textContent = isLoginMode ? '登录' : '注册';
    if (isLoginMode) {
        toggleLink.textContent = '注册新账号';
        document.querySelector('.login-extra').innerHTML = `<a href="#" id="toggle-link">注册新账号</a><span>|</span><a href="#">忘记密码?</a>`;
    } else {
        toggleLink.textContent = '返回登录';
        document.querySelector('.login-extra').innerHTML = `<a href="#" id="toggle-link">返回登录</a>`;
    }
    errorMsg.textContent = '';
    document.getElementById('toggle-link').addEventListener('click', (e) => {
        e.preventDefault();
        toggleMode();
    });
}

// ========== 页面状态切换 ==========
function showLoginPage() {
    loginPage.style.display = 'flex';
    gameHall.style.display = 'none';
    removeToken();
    removeUsername();
}
function showGameHall(username) {
    loginPage.style.display = 'none';
    gameHall.style.display = 'flex';
    displayUsername.textContent = username;
    heroUsername.textContent = username;
    // 默认显示主页
    switchPage('home');
}

// 导航页切换
function switchPage(pageName) {
    // 更新导航激活状态
    navItems.forEach(item => {
        item.classList.remove('active');
        if (item.dataset.page === pageName) item.classList.add('active');
    });
    // 显示对应页面
    pages.forEach(page => {
        page.classList.remove('active');
        if (page.id === `page-${pageName}`) page.classList.add('active');
    });
    if (pageName === 'maze') {
        if (modeSelectDiv) modeSelectDiv.style.display = 'block';
        if (singleSetupDiv) singleSetupDiv.style.display = 'none';
        if (mazeGameContainer) mazeGameContainer.style.display = 'none';
        if (mazeConfigPanel) mazeConfigPanel.style.display = 'block';
    }
}

// 绑定导航点击事件
navItems.forEach(item => {
    item.addEventListener('click', () => {
        switchPage(item.dataset.page);
    });
});

// 主页的快速开始按钮（跳转到迷宫页）
document.getElementById('start-maze-btn').addEventListener('click', () => {
    switchPage('maze');
});

// ========== 认证流程 ==========
authForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    errorMsg.textContent = '';

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    if (!username || !password) {
        errorMsg.textContent = '请输入召唤师昵称和密码';
        return;
    }

    const url = isLoginMode ? '/auth/login' : '/auth/register';
    try {
        const result = await request(url, {
            method: 'POST',
            body: JSON.stringify({ username, password }),
        });
        const token = result.data;
        setToken(token);
        setUsername(username);

        // 解析 JWT payload 获取 userId
        const payload = JSON.parse(atob(token.split('.')[1]));
        setUserId(payload.userId);

        showGameHall(username);
    } catch (err) {
        errorMsg.textContent = err.message;
    }
});

// 登出
logoutBtn.addEventListener('click', async () => {
    try {
        await request('/auth/logout', { method: 'POST' });
    } catch (e) {
        console.warn('登出异常：', e.message);
    } finally {
        removeToken();
        removeUsername();
        showLoginPage();
    }
});

// 注册/登录切换
toggleLink.addEventListener('click', (e) => {
    e.preventDefault();
    toggleMode();
});
// ========== 模式选择逻辑 ==========
const modeSelectDiv = document.getElementById('mode-select');
const singleSetupDiv = document.getElementById('single-setup');

// ====== 单人迷宫游戏逻辑 ======
let currentMazeData = null;      // 当前迷宫实体
let grid = [];                  // 二维网格 0=路 1=墙
let playerPos = { row: 1, col: 0 };
let cellSize = 20;
let gamePaused = false;
let gameWon = false;
let moving = false;             // 移动锁，防止请求堆积

// 单人计时变量
let gameStartTime = null;
let timerInterval = null;

// DOM 元素
const mazeConfigPanel = document.getElementById('maze-config-panel');
const mazeGameContainer = document.getElementById('maze-game-container');
const mazeCanvas = document.getElementById('maze-canvas');
const ctx = mazeCanvas ? mazeCanvas.getContext('2d') : null;

// 离屏背景 Canvas
const bgCanvas = document.getElementById('maze-bg-canvas');
const bgCtx = bgCanvas ? bgCanvas.getContext('2d') : null;

// 计时器工具函数
function formatTime(sec) {
    const m = String(Math.floor(sec / 60)).padStart(2, '0');
    const s = String(sec % 60).padStart(2, '0');
    return `${m}:${s}`;
}

function updateTimerDisplay() {
    const el = document.getElementById('timer-display');
    if (el && gameStartTime) {
        const elapsed = Math.floor((Date.now() - gameStartTime) / 1000);
        el.textContent = formatTime(elapsed);
    } else if (el) {
        el.textContent = '00:00';
    }
}
function startTimer() {
    stopTimer();
    gameStartTime = Date.now();
    updateTimerDisplay();
    timerInterval = setInterval(updateTimerDisplay, 1000);
}
function stopTimer() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
    updateTimerDisplay();
}
function pauseTimer() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}
function resumeTimer() {
    if (!gameStartTime || timerInterval) return;
    updateTimerDisplay();
    timerInterval = setInterval(updateTimerDisplay, 1000);
}
function resetTimer() {
    stopTimer();
    gameStartTime = null;
    document.getElementById('timer-display').textContent = '00:00';
}

// 生成迷宫按钮
document.getElementById('generate-maze-btn').addEventListener('click', async () => {
    const rows = parseInt(document.getElementById('maze-rows').value) || 21;
    const cols = parseInt(document.getElementById('maze-cols').value) || 21;
    const algorithm = document.getElementById('maze-algorithm').value;

    try {
        const result = await request('/maze/generate', {
            method: 'POST',
            body: JSON.stringify({ rows, cols, algorithm }),
        });
        currentMazeData = result.data;
        grid = JSON.parse(currentMazeData.gridData);
        playerPos = { row: currentMazeData.playerRow, col: currentMazeData.playerCol };
        gameWon = false;
        gamePaused = false;
        document.getElementById('game-status').textContent = '探索中...';

        // 切换到游戏画面
        mazeConfigPanel.style.display = 'none';
        mazeGameContainer.style.display = 'block';

        // 调整尺寸并绘制静态背景
        resizeCanvas();
        drawStaticBackground();

        // 绘制玩家初始位置
        if (ctx) drawPlayer();
        startTimer();  // 开始计时
    } catch (e) {
        alert('生成迷宫失败：' + e.message);
    }
});

// 重写返回按钮逻辑（确保参数面板显示）
document.getElementById('back-to-mode').addEventListener('click', () => {
    const doBack = () => {
        document.getElementById('single-setup').style.display = 'none';
        document.getElementById('mode-select').style.display = 'block';
        if (mazeGameContainer) mazeGameContainer.style.display = 'none';
        if (mazeConfigPanel) mazeConfigPanel.style.display = 'block';
        if (multiContainer) multiContainer.style.display = 'none';
        currentMazeData = null;
        grid = [];
        gameWon = false;
        gamePaused = false;
        resetTimer(); // 重置计时
    };

    if (currentMazeData && currentMazeData.isSaved === 0 && mazeGameContainer.style.display !== 'none') {
        showExitConfirmation(doBack, doBack);
    } else {
        doBack();
    }
});

// 画布尺寸调整
function resizeCanvas() {
    if (!grid.length) return;
    const rows = grid.length, cols = grid[0].length;
    const maxWidth = Math.min(window.innerWidth * 0.7, 800);
    const maxHeight = window.innerHeight * 0.6;
    cellSize = Math.min(30, Math.floor(maxWidth / cols), Math.floor(maxHeight / rows));
    mazeCanvas.width = cols * cellSize;
    mazeCanvas.height = rows * cellSize;

    // 背景 Canvas 同步尺寸
    bgCanvas.width = mazeCanvas.width;
    bgCanvas.height = mazeCanvas.height;
}

// 绘制静态背景（仅一次，保存到离屏 Canvas）
function drawStaticBackground() {
    if (!bgCtx || !grid.length) return;
    const rows = grid.length, cols = grid[0].length;
    bgCtx.clearRect(0, 0, bgCanvas.width, bgCanvas.height);

    // 绘制道路和墙壁
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const x = c * cellSize, y = r * cellSize;
            if (grid[r][c] === 1) {
                bgCtx.fillStyle = '#2a2a2a';
                bgCtx.fillRect(x, y, cellSize, cellSize);
            } else {
                bgCtx.fillStyle = '#1a1a1a';
                bgCtx.fillRect(x, y, cellSize, cellSize);
            }
        }
    }

    // 起点
    const start = { row: currentMazeData.startRow, col: currentMazeData.startCol };
    bgCtx.fillStyle = '#2d5a27';
    bgCtx.fillRect(start.col * cellSize, start.row * cellSize, cellSize, cellSize);
    bgCtx.fillStyle = '#4f8a4b';
    bgCtx.font = `${cellSize * 0.6}px sans-serif`;
    bgCtx.fillText('入', start.col * cellSize + cellSize*0.2, start.row * cellSize + cellSize*0.7);

    // 终点
    const end = { row: currentMazeData.endRow, col: currentMazeData.endCol };
    bgCtx.fillStyle = '#5a2727';
    bgCtx.fillRect(end.col * cellSize, end.row * cellSize, cellSize, cellSize);
    bgCtx.fillStyle = '#c8aa6e';
    bgCtx.fillText('終', end.col * cellSize + cellSize*0.2, end.row * cellSize + cellSize*0.7);

    // 道具（如果有）
    if (currentMazeData.itemPositions) {
        const items = JSON.parse(currentMazeData.itemPositions);
        items.forEach(item => {
            bgCtx.fillStyle = '#c8aa6e';
            bgCtx.beginPath();
            bgCtx.arc(item.col * cellSize + cellSize/2, item.row * cellSize + cellSize/2, cellSize*0.3, 0, Math.PI*2);
            bgCtx.fill();
        });
    }
}

// 绘制玩家（只绘制动态元素）
function drawPlayer() {
    if (!ctx) return;
    ctx.clearRect(0, 0, mazeCanvas.width, mazeCanvas.height);
    ctx.drawImage(bgCanvas, 0, 0);

    ctx.fillStyle = gameWon ? '#ffd700' : '#e94560';
    ctx.beginPath();
    ctx.arc(playerPos.col * cellSize + cellSize/2, playerPos.row * cellSize + cellSize/2, cellSize*0.35, 0, Math.PI*2);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = `${cellSize*0.5}px sans-serif`;
    ctx.fillText('⚔️', playerPos.col * cellSize + cellSize*0.15, playerPos.row * cellSize + cellSize*0.7);

    if (gamePaused) {
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        ctx.fillRect(0, 0, mazeCanvas.width, mazeCanvas.height);
        ctx.fillStyle = '#c8aa6e';
        ctx.font = `${cellSize}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.fillText('暂停中', mazeCanvas.width/2, mazeCanvas.height/2);
        ctx.textAlign = 'start';
    } else if (gameWon) {
        ctx.fillStyle = 'rgba(0,0,0,0.4)';
        ctx.fillRect(0, 0, mazeCanvas.width, mazeCanvas.height);
        ctx.fillStyle = '#ffd700';
        ctx.font = `bold ${cellSize}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.fillText('通关！', mazeCanvas.width/2, mazeCanvas.height/2);
        ctx.textAlign = 'start';
    }
}

// 移动处理（带锁）
async function movePlayer(direction) {
    if (moving || gamePaused || gameWon || !currentMazeData) return;
    moving = true;
    try {
        const result = await request('/maze/move', {
            method: 'POST',
            body: JSON.stringify({ direction }),
        });
        playerPos.row = result.data.row;
        playerPos.col = result.data.col;
        gameWon = result.data.won;

        if (gameWon) {
            document.getElementById('game-status').textContent = '🎉 恭喜通关！';
            stopTimer(); // 停止计时
            if (result.data.elapsedSeconds != null) {
                document.getElementById('timer-display').textContent = formatTime(result.data.elapsedSeconds);
            }
        }
        drawPlayer();
    } catch (e) {
        console.log('移动失败：' + e.message);
    } finally {
        moving = false;
    }
}

// 键盘监听
window.addEventListener('keydown', (e) => {
    if (!mazeGameContainer || mazeGameContainer.style.display === 'none') return;
    const key = e.key.toLowerCase();
    if (key === 'w' || key === 'arrowup') {
        e.preventDefault();
        movePlayer('up');
    } else if (key === 's' || key === 'arrowdown') {
        e.preventDefault();
        movePlayer('down');
    } else if (key === 'a' || key === 'arrowleft') {
        e.preventDefault();
        movePlayer('left');
    } else if (key === 'd' || key === 'arrowright') {
        e.preventDefault();
        movePlayer('right');
    }
});

// 工具栏按钮
document.getElementById('pause-game-btn').addEventListener('click', () => {
    gamePaused = !gamePaused;
    document.getElementById('pause-game-btn').textContent = gamePaused ? '继续' : '暂停';
    if (gamePaused) {
        pauseTimer();
    } else {
        resumeTimer();
    }
    drawPlayer();
});

document.getElementById('reset-game-btn').addEventListener('click', () => {
    if (!currentMazeData) return;
    gamePaused = false;
    gameWon = false;
    playerPos = { row: currentMazeData.startRow, col: currentMazeData.startCol };
    document.getElementById('game-status').textContent = '探索中...';
    drawPlayer();
});

document.getElementById('new-maze-btn').addEventListener('click', () => {
    const doNewMaze = () => {
        currentMazeData = null;
        grid = [];
        gameWon = false;
        gamePaused = false;
        resetTimer();
        mazeGameContainer.style.display = 'none';
        mazeConfigPanel.style.display = 'block';
    };

    if (currentMazeData && currentMazeData.isSaved === 0) {
        showExitConfirmation(doNewMaze, doNewMaze); // 保存后执行 或 直接执行
    } else {
        doNewMaze();
    }
});

// 窗口大小变化时重绘背景和玩家
window.addEventListener('resize', () => {
    if (mazeGameContainer && mazeGameContainer.style.display === 'block' && currentMazeData) {
        resizeCanvas();
        drawStaticBackground();
        drawPlayer();
    }
});

// 调整单人模式入口（确保参数面板显示）
document.getElementById('single-mode-btn').addEventListener('click', () => {
    modeSelectDiv.style.display = 'none';
    singleSetupDiv.style.display = 'block';
    mazeConfigPanel.style.display = 'block';
    mazeGameContainer.style.display = 'none';
    if (multiContainer) multiContainer.style.display = 'none';
});
// ====== 多人迷宫 WebSocket 逻辑 ======
let ws = null;
let multiRoomId = null;
let multiUserIdStr = null;     // 当前用户的 userId（从 token 解析或登录后存储）
let multiGrid = [];
let multiEndRow = -1, multiEndCol = -1;
let multiPlayersPos = {};      // userId -> { row, col }
let multiReadySet = new Set();
let multiHostId = null;
let multiStarted = false;
let multiWon = false;
let multiMoving = false;
let multiCellSize = 20;

// 多人计时变量
let multiGameStartTime = null;
let multiTimerInterval = null;

// DOM
const multiContainer = document.getElementById('multi-container');
const roomLobby = document.getElementById('room-lobby');
const waitingRoom = document.getElementById('waiting-room');
const multiGameContainer = document.getElementById('multi-game-container');
const multiCanvas = document.getElementById('multi-canvas');
const multiCtx = multiCanvas ? multiCanvas.getContext('2d') : null;
const multiBgCanvas = document.getElementById('multi-bg-canvas');
const multiBgCtx = multiBgCanvas ? multiBgCanvas.getContext('2d') : null;

// 多人计时工具函数
function updateMultiTimerDisplay() {
    const el = document.getElementById('multi-timer-display');
    if (el && multiGameStartTime) {
        const elapsed = Math.floor((Date.now() - multiGameStartTime) / 1000);
        el.textContent = formatTime(elapsed);
    } else if (el) {
        el.textContent = '00:00';
    }
}
function startMultiTimer() {
    stopMultiTimer();
    multiGameStartTime = Date.now();
    updateMultiTimerDisplay();
    multiTimerInterval = setInterval(updateMultiTimerDisplay, 1000);
}
function stopMultiTimer() {
    if (multiTimerInterval) {
        clearInterval(multiTimerInterval);
        multiTimerInterval = null;
    }
    updateMultiTimerDisplay();
}
function resetMultiTimer() {
    stopMultiTimer();
    multiGameStartTime = null;
    document.getElementById('multi-timer-display').textContent = '00:00';
}

// 连接 WebSocket
function connectMultiWS() {
    const token = getToken();
    ws = new WebSocket(`ws://${location.host}/ws/maze?token=` + token);
    ws.onopen = () => console.log('多人WebSocket已连接');
    ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        handleWSMessage(msg);
    };
    ws.onclose = () => {
        console.log('多人WebSocket断开');
        resetMultiUI();
    };
    ws.onerror = (err) => console.error('WebSocket错误', err);
}

function handleWSMessage(msg) {
    const { type, data } = msg;
    switch (type) {
        case 'room_info': updateRoomInfo(data); break;
        case 'player_joined': addPlayerToList(data.userId); break;
        case 'player_left': removePlayerFromList(data.userId, data.newHost); break;
        case 'player_ready': updateReadyStatus(data.userId, data.ready); break;
        case 'game_started': startMultiGame(data); break;
        case 'player_moved': updateMultiPosition(data.userId, data.row, data.col); break;
        case 'winner': showMultiWinner(data.userId, data.elapsedSeconds); break;
        case 'kicked': alert(data.message || '你被踢出了房间'); resetMultiUI(); break;
        case 'left': resetMultiUI(); break;
        case 'error': document.getElementById('room-message').textContent = data; break;
    }
}

function sendWS(type, data) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type, data }));
    }
}

// ---- 界面切换 ----
document.getElementById('multi-mode-btn').addEventListener('click', () => {
    modeSelectDiv.style.display = 'none';
    multiContainer.style.display = 'block';
    roomLobby.style.display = 'block';
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'none';
    if (!ws || ws.readyState !== WebSocket.OPEN) connectMultiWS();
});

document.getElementById('back-to-mode-multi').addEventListener('click', () => {
    multiContainer.style.display = 'none';
    modeSelectDiv.style.display = 'block';
    if (multiRoomId) sendWS('leave', { roomId: multiRoomId });
    resetMultiUI();
});

// ---- 房间操作 ----
document.getElementById('create-room-btn').addEventListener('click', () => {
    sendWS('create', { rows: 21, cols: 21, algorithm: 'dfs' });
});

document.getElementById('join-room-btn').addEventListener('click', () => {
    const roomId = document.getElementById('room-id-input').value.trim();
    if (roomId) sendWS('join', { roomId });
    else document.getElementById('room-message').textContent = '请输入房间号';
});

document.getElementById('ready-btn').addEventListener('click', () => {
    const btn = document.getElementById('ready-btn');
    if (btn.textContent === '准备') {
        sendWS('ready', { roomId: multiRoomId });
        btn.textContent = '取消准备';
    } else {
        sendWS('unready', { roomId: multiRoomId });
        btn.textContent = '准备';
    }
});

document.getElementById('start-game-btn').addEventListener('click', () => {
    sendWS('start', { roomId: multiRoomId });
});

document.getElementById('leave-room-btn').addEventListener('click', () => {
    sendWS('leave', { roomId: multiRoomId });
    resetMultiUI();
    roomLobby.style.display = 'block';
});

document.getElementById('kick-player-btn').addEventListener('click', () => {
    const target = document.getElementById('kick-target-select').value;
    if (target) sendWS('kick', { roomId: multiRoomId, targetUserId: target });
});

// ---- 房间信息更新 ----
function updateRoomInfo(data) {
    multiPlayersPos = data.positions || {};
    multiRoomId = data.roomId;
    multiHostId = data.host;
    multiGrid = data.grid;
    multiEndRow = data.endRow;
    multiEndCol = data.endCol;
    multiStarted = data.started;

    document.getElementById('current-room-id').textContent = multiRoomId;
    document.getElementById('room-host-badge').style.display = (multiHostId === getUserId()) ? 'inline' : 'none';
    document.getElementById('start-game-btn').style.display = (multiHostId === getUserId()) ? 'inline-block' : 'none';

    roomLobby.style.display = 'none';
    waitingRoom.style.display = 'block';

    // 清空并重建玩家列表
    document.getElementById('kick-target-select').innerHTML = ''; // 清空旧选项
    document.getElementById('player-list').innerHTML = '';
    if (data.players) data.players.forEach(p => addPlayerToList(p));
    if (data.readyPlayers) data.readyPlayers.forEach(p => multiReadySet.add(p));

    updateReadyUI();
}

function addPlayerToList(userId) {
    const list = document.getElementById('player-list');
    const existing = document.getElementById(`player-${userId}`);
    if (existing) return;
    const div = document.createElement('div');
    div.className = 'player-item';
    div.id = `player-${userId}`;
    div.innerHTML = `<span class="player-name">${userId}${userId === multiHostId ? '<span class="host-tag">👑房主</span>' : ''}</span>
                     <span class="ready-tag">${multiReadySet.has(userId) ? '✅已准备' : '⏳未准备'}</span>`;
    list.appendChild(div);
    document.getElementById('room-player-count').textContent = `${list.children.length}/4 人`;
    // 如果自己是房主，显示踢人下拉
    if (getUserId() === multiHostId && userId !== getUserId()) {
        document.getElementById('kick-player-btn').style.display = 'inline-block';
        document.getElementById('kick-target-select').style.display = 'inline-block';
        const opt = document.createElement('option');
        opt.value = userId; opt.textContent = userId;
        document.getElementById('kick-target-select').appendChild(opt);
    }
}

function removePlayerFromList(userId, newHost) {
    const item = document.getElementById(`player-${userId}`);
    if (item) item.remove();
    const list = document.getElementById('player-list');
    document.getElementById('room-player-count').textContent = `${list.children.length}/4 人`;
    if (newHost) {
        multiHostId = newHost;
        document.getElementById('room-host-badge').style.display = (multiHostId === getUserId()) ? 'inline' : 'none';
        document.getElementById('start-game-btn').style.display = (multiHostId === getUserId()) ? 'inline-block' : 'none';
    }
}

function updateReadyStatus(userId, ready) {
    if (ready) multiReadySet.add(userId);
    else multiReadySet.delete(userId);
    const item = document.getElementById(`player-${userId}`);
    if (item) item.querySelector('.ready-tag').textContent = ready ? '✅已准备' : '⏳未准备';
    updateReadyUI();
}

function updateReadyUI() {
    document.getElementById('start-game-btn').disabled = !(multiReadySet.size >= 2); // 至少2人准备
}

// ---- 游戏开始/进行 ----
function startMultiGame(data) {
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'block';
    multiStarted = true;

    // 更新迷宫数据（第二局已不同）
    if (data.grid) multiGrid = data.grid;
    if (data.endRow !== undefined) multiEndRow = data.endRow;
    if (data.endCol !== undefined) multiEndCol = data.endCol;
    if (data.positions) multiPlayersPos = data.positions;

    document.getElementById('multi-game-status').textContent = '竞速中...';
    initMultiCanvas();
    drawMultiMaze();
    startMultiTimer();  // 开始多人计时
}

function initMultiCanvas() {
    if (!multiGrid.length) return;
    const rows = multiGrid.length, cols = multiGrid[0].length;
    const maxW = Math.min(window.innerWidth * 0.7, 800);
    const maxH = window.innerHeight * 0.6;
    multiCellSize = Math.min(30, Math.floor(maxW / cols), Math.floor(maxH / rows));
    multiCanvas.width = cols * multiCellSize;
    multiCanvas.height = rows * multiCellSize;
    multiBgCanvas.width = multiCanvas.width;
    multiBgCanvas.height = multiCanvas.height;
    drawStaticMultiBg();
}

function drawStaticMultiBg() {
    if (!multiBgCtx || !multiGrid.length) return;
    multiBgCtx.clearRect(0, 0, multiBgCanvas.width, multiBgCanvas.height);
    for (let r = 0; r < multiGrid.length; r++) {
        for (let c = 0; c < multiGrid[0].length; c++) {
            multiBgCtx.fillStyle = multiGrid[r][c] === 1 ? '#2a2a2a' : '#1a1a1a';
            multiBgCtx.fillRect(c * multiCellSize, r * multiCellSize, multiCellSize, multiCellSize);
        }
    }

    // ✅ 起点（所有玩家起点固定为 row=1, col=0）
    const startRow = 1, startCol = 0;
    multiBgCtx.fillStyle = '#2d5a27';
    multiBgCtx.fillRect(startCol * multiCellSize, startRow * multiCellSize, multiCellSize, multiCellSize);
    multiBgCtx.fillStyle = '#4f8a4b';
    multiBgCtx.font = `${multiCellSize*0.6}px sans-serif`;
    multiBgCtx.fillText('入', startCol * multiCellSize + multiCellSize*0.2, startRow * multiCellSize + multiCellSize*0.7);

    // 终点
    multiBgCtx.fillStyle = '#5a2727';
    multiBgCtx.fillRect(multiEndCol * multiCellSize, multiEndRow * multiCellSize, multiCellSize, multiCellSize);
    multiBgCtx.fillStyle = '#c8aa6e';
    multiBgCtx.font = `${multiCellSize*0.6}px sans-serif`;
    multiBgCtx.fillText('終', multiEndCol * multiCellSize + multiCellSize*0.2, multiEndRow * multiCellSize + multiCellSize*0.7);
}

function drawMultiMaze() {
    if (!multiCtx || !multiBgCanvas) return;
    multiCtx.clearRect(0, 0, multiCanvas.width, multiCanvas.height);
    multiCtx.drawImage(multiBgCanvas, 0, 0);
    // 绘制所有玩家
    const colors = ['#e94560', '#4fc3f7', '#ffb74d', '#81c784'];
    let idx = 0;
    for (const [uid, pos] of Object.entries(multiPlayersPos)) {
        multiCtx.fillStyle = uid === getUserId() ? '#ffd700' : colors[idx % colors.length];
        multiCtx.beginPath();
        multiCtx.arc(pos.col * multiCellSize + multiCellSize/2, pos.row * multiCellSize + multiCellSize/2, multiCellSize*0.35, 0, Math.PI*2);
        multiCtx.fill();
        idx++;
    }
}

function updateMultiPosition(userId, row, col) {
    multiPlayersPos[userId] = { row, col };
    drawMultiMaze();
}

function showMultiWinner(userId, elapsedSeconds) {
    multiWon = true;
    stopMultiTimer();
    if (elapsedSeconds !== undefined) {
        document.getElementById('multi-timer-display').textContent = formatTime(elapsedSeconds);
    }
    const timeStr = elapsedSeconds !== undefined ? formatTime(elapsedSeconds) : '未知';
    document.getElementById('multi-game-status').textContent = `🏆 ${userId} 获胜！用时 ${timeStr}`;
    alert(`${userId} 率先到达终点！用时 ${timeStr}`);
    drawMultiMaze();

    // 自动返回房间等待室
    setTimeout(() => {
        multiGameContainer.style.display = 'none';
        waitingRoom.style.display = 'block';

        multiStarted = false;
        multiWon = false;
        resetMultiTimer();

        // 清除所有准备状态
        multiReadySet.clear();
        document.querySelectorAll('#player-list .ready-tag').forEach(tag => {
            tag.textContent = '⏳未准备';
        });

        // 房主显示开始游戏按钮（需重新准备后启用）
        if (multiHostId === getUserId()) {
            document.getElementById('start-game-btn').style.display = 'inline-block';
            document.getElementById('start-game-btn').disabled = true;
        }
        const readyBtn = document.getElementById('ready-btn');
        if (readyBtn) readyBtn.textContent = '准备';
    }, 1500);
}

// ---- 移动 ----
async function multiMovePlayer(direction) {
    if (multiMoving || multiWon || !multiStarted) return;
    multiMoving = true;
    sendWS('move', { roomId: multiRoomId, direction });
    // 本地也乐观更新（可选，这里等服务器广播后再更新）
    multiMoving = false;
}

// ---- 重置 ----
function resetMultiUI() {
    roomLobby.style.display = 'block';
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'none';
    multiRoomId = null;
    multiGrid = [];
    multiPlayersPos = {};
    multiReadySet.clear();
    multiStarted = false;
    multiWon = false;
    resetMultiTimer();  // 重置多人计时
    document.getElementById('room-id-input').value = '';
    document.getElementById('room-message').textContent = '';
    document.getElementById('kick-target-select').innerHTML = '';
}

// 多人模式键盘监听
window.addEventListener('keydown', (e) => {
    if (!multiGameContainer || multiGameContainer.style.display === 'none') return;
    const key = e.key.toLowerCase();
    if (key === 'w' || key === 'arrowup') { e.preventDefault(); multiMovePlayer('up'); }
    else if (key === 's' || key === 'arrowdown') { e.preventDefault(); multiMovePlayer('down'); }
    else if (key === 'a' || key === 'arrowleft') { e.preventDefault(); multiMovePlayer('left'); }
    else if (key === 'd' || key === 'arrowright') { e.preventDefault(); multiMovePlayer('right'); }
});

// 多人工具栏（只保留退出房间按钮）
document.getElementById('multi-leave-game-btn').addEventListener('click', () => {
    sendWS('leave', { roomId: multiRoomId });
    resetMultiUI();
    roomLobby.style.display = 'block';
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'none';
});

// 确保进入多人模式时显示正确
// 修改 switchPage 函数，添加多人容器隐藏逻辑
const origSwitchPage = switchPage;  // 只保留这一次声明
switchPage = function(pageName) {
    origSwitchPage(pageName);
    if (pageName === 'maze') {
        if (multiContainer) multiContainer.style.display = 'none';
        updateSavedPanelVisibility();   // ← 加入这行
    }
};
// ========== 我的存档按钮 ==========
document.getElementById('view-saves-btn').addEventListener('click', () => {
    const panel = document.getElementById('saved-mazes-panel');
    // 强制显示存档面板
    panel.style.display = 'block';
    // 重新加载存档列表
    loadSavedMazesList();
    // 平滑滚动到存档区域（300ms 防抖，避免按钮在动画期间触发多次）
    setTimeout(() => {
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
});
// ========== 初始化 ==========
function init() {
    const token = getToken();
    const username = getUsername();
    if (token && username) {
        showGameHall(username);
    } else {
        showLoginPage();
    }
}
init();
// ========== 存档相关 DOM 元素 ==========
const saveGameBtn = document.getElementById('save-game-btn');
const saveModal = document.getElementById('save-modal');
const saveMazeNameInput = document.getElementById('save-maze-name');
const confirmSaveBtn = document.getElementById('confirm-save-btn');
const cancelSaveBtn = document.getElementById('cancel-save-btn');
const saveModalError = document.getElementById('save-modal-error');

const loadConflictModal = document.getElementById('load-conflict-modal');
const conflictSaveLoadBtn = document.getElementById('conflict-save-load-btn');
const conflictDiscardLoadBtn = document.getElementById('conflict-discard-load-btn');
const conflictCancelBtn = document.getElementById('conflict-cancel-btn');
const conflictNameGroup = document.getElementById('conflict-name-group');
const conflictSaveNameInput = document.getElementById('conflict-save-name');

const savedMazesPanel = document.getElementById('saved-mazes-panel');
const savedMazesList = document.getElementById('saved-mazes-list');

let pendingLoadId = null;

// ========== 保存按钮 ==========
saveGameBtn.addEventListener('click', () => {
    if (!currentMazeData) {
        alert('没有正在进行的迷宫，无法保存');
        return;
    }
    saveMazeNameInput.value = '';
    saveModalError.textContent = '';
    saveModal.style.display = 'flex';
});

cancelSaveBtn.addEventListener('click', () => {
    saveModal.style.display = 'none';
});

confirmSaveBtn.addEventListener('click', async () => {
    const mazeName = saveMazeNameInput.value.trim();
    if (!mazeName) {
        saveModalError.textContent = '请输入存档名称';
        return;
    }
    try {
        await request('/maze/save', {
            method: 'POST',
            body: JSON.stringify({ mazeName })
        });
        if (currentMazeData) {
                    currentMazeData.isSaved = 1;
                    currentMazeData.mazeName = mazeName; // 可选
        }
        saveModal.style.display = 'none';
        alert('迷宫保存成功！');
        await loadSavedMazesList();
    } catch (e) {
        saveModalError.textContent = e.message;
    }
});

// ========== 存档列表 ==========
async function loadSavedMazesList() {
    try {
        const result = await request('/maze/saved');   // ✅ 正确接口
        const saves = result.data;
        savedMazesList.innerHTML = saves.length ? '' : '<p style="color:#9b9b9b;">暂无存档</p>';
        saves.forEach(save => {
            const item = document.createElement('div');
            item.className = 'save-item';
            item.innerHTML = `
                <span class="save-name">📁 ${save.mazeName}</span>
                <span class="save-date">${new Date(save.savedAt).toLocaleString()}</span>
                <button class="action-btn small load-save-btn" data-id="${save.id}">加载</button>
            `;
            savedMazesList.appendChild(item);
        });

        document.querySelectorAll('.load-save-btn').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const saveId = Number(e.target.dataset.id);
                if (currentMazeData) {
                    pendingLoadId = saveId;
                    loadConflictModal.style.display = 'flex';
                    conflictNameGroup.style.display = 'none';
                } else {
                    await loadMazeById(saveId);
                }
            });
        });
    } catch (e) {
        console.error('获取存档列表失败:', e.message);
        savedMazesList.innerHTML = '<p style="color:#e94560;">加载存档列表失败</p>';
    }
}

async function loadMazeById(saveId) {
    try {
        const result = await request('/maze/load', {
            method: 'POST',
            body: JSON.stringify({ mazeId: saveId, saveCurrent: false, currentMazeName: '' })
        });
        currentMazeData = result.data;
        grid = JSON.parse(currentMazeData.gridData);
        playerPos = { row: currentMazeData.playerRow, col: currentMazeData.playerCol };
        gameWon = false;
        gamePaused = false;
        document.getElementById('game-status').textContent = '探索中...';

        mazeConfigPanel.style.display = 'none';
        mazeGameContainer.style.display = 'block';
        resizeCanvas();
        drawStaticBackground();
        drawPlayer();
        startTimer();  // 开始计时
    } catch (e) {
        alert('加载迷宫失败：' + e.message);
    }
}

// ========== 冲突模态框 ==========
conflictSaveLoadBtn.addEventListener('click', async () => {
    conflictNameGroup.style.display = 'block';
    const mazeName = conflictSaveNameInput.value.trim();
    if (!mazeName) {
        alert('请输入当前迷宫的名称');
        return;
    }
    try {
        await request('/maze/save', {
            method: 'POST',
            body: JSON.stringify({ mazeName })
        });
        loadConflictModal.style.display = 'none';
        await loadMazeById(pendingLoadId);
        pendingLoadId = null;
    } catch (e) {
        alert('操作失败：' + e.message);
    }
});
// ========== 退出迷宫确认相关 ==========
const exitModal = document.getElementById('exit-maze-modal');
const exitSaveBtn = document.getElementById('exit-save-btn');
const exitDiscardBtn = document.getElementById('exit-discard-btn');
const exitCancelBtn = document.getElementById('exit-cancel-btn');
const exitSaveNameInput = document.getElementById('exit-save-name');
const exitModalError = document.getElementById('exit-modal-error');
let pendingExitAction = null;

function showExitConfirmation(onSaveAndExit, onDiscard) {
    // 如果没有迷宫或已经保存，直接执行丢弃操作（无需弹窗）
    if (!currentMazeData || currentMazeData.isSaved === 1) {
        if (onDiscard) onDiscard();
        return;
    }
    exitSaveNameInput.value = '';
    exitModalError.textContent = '';
    pendingExitAction = { onSaveAndExit, onDiscard };
    exitModal.style.display = 'flex';
}

function hideExitModal() {
    exitModal.style.display = 'none';
    pendingExitAction = null;
}

exitSaveBtn.addEventListener('click', async () => {
    const name = exitSaveNameInput.value.trim();
    if (!name) {
        exitModalError.textContent = '请输入存档名称';
        return;
    }
    try {
        await request('/maze/save', {
            method: 'POST',
            body: JSON.stringify({ mazeName: name })
        });
        if (currentMazeData) {
            currentMazeData.isSaved = 1;
            currentMazeData.mazeName = name;
        }
        const action = pendingExitAction;   // ← 保存
        hideExitModal();                    // ← 隐藏
        if (action && action.onSaveAndExit) {   // ✅ 改用 action
                    action.onSaveAndExit();
        }
    } catch (e) {
        exitModalError.textContent = e.message;
    }
});

exitDiscardBtn.addEventListener('click', () => {
    const action = pendingExitAction;   // ← 先保存
    hideExitModal();                    // ← 再隐藏（会清空 pendingExitAction）
    if (action && action.onDiscard) {
        action.onDiscard();
    }
});

exitCancelBtn.addEventListener('click', hideExitModal);
conflictDiscardLoadBtn.addEventListener('click', async () => {
    loadConflictModal.style.display = 'none';
    await loadMazeById(pendingLoadId);
    pendingLoadId = null;
});

conflictCancelBtn.addEventListener('click', () => {
    loadConflictModal.style.display = 'none';
    pendingLoadId = null;
});

// ========== 存档面板显示/隐藏 ==========
function updateSavedPanelVisibility() {
    const show = (singleSetupDiv.style.display !== 'none' && mazeGameContainer.style.display === 'none');
    if (show) {
        savedMazesPanel.style.display = 'block';
        loadSavedMazesList();
    } else {
        savedMazesPanel.style.display = 'none';
    }
}

// 监听游戏容器显示状态变化
const observer = new MutationObserver(() => {
    updateSavedPanelVisibility();
});
observer.observe(mazeGameContainer, { attributes: true, attributeFilter: ['style'] });

// 浏览器关闭/刷新前提醒（仅当迷宫未保存）
window.addEventListener('beforeunload', (e) => {
    if (currentMazeData && currentMazeData.isSaved === 0) {
        e.preventDefault();
        e.returnValue = '您还有未保存的迷宫进度，确定离开吗？';
        return e.returnValue;
    }
});