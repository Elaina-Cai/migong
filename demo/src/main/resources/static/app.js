// ========== 配置 ==========
const API_BASE = '';                // 同源请求
const TOKEN_KEY = 'maze_token';
const USERNAME_KEY = 'maze_username';
const USERID_KEY = 'maze_userid';

// ========== 工具函数 ==========
function setUserId(id) { localStorage.setItem(USERID_KEY, id); }
function getUserId() {
    let id = localStorage.getItem(USERID_KEY);
    if (!id) {
        const token = getToken();
        if (token) {
            try {
                const payload = JSON.parse(atob(token.split('.')[1]));
                id = payload.userId;
                if (id) setUserId(id);
            } catch (e) {}
        }
    }
    return id ? String(id) : null;
}
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

let isLoginMode = true;

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
    if (ws) {
        ws.close();
        ws = null;
        resetMultiUI();
    }
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
    switchPage('home');
}

function switchPage(pageName) {
    navItems.forEach(item => {
        item.classList.remove('active');
        if (item.dataset.page === pageName) item.classList.add('active');
    });
    pages.forEach(page => {
        page.classList.remove('active');
        if (page.id === `page-${pageName}`) page.classList.add('active');
    });
    if (pageName === 'maze') {
        if (multiInRoom) {
            modeSelectDiv.style.display = 'none';
            singleSetupDiv.style.display = 'none';
            if (mazeConfigPanel) mazeConfigPanel.style.display = 'none';
            if (mazeGameContainer) mazeGameContainer.style.display = 'none';
            if (savedMazesPanel) savedMazesPanel.style.display = 'none';
            multiContainer.style.display = 'block';
            if (multiStarted) {
                waitingRoom.style.display = 'none';
                multiGameContainer.style.display = 'block';
            } else {
                waitingRoom.style.display = 'block';
                multiGameContainer.style.display = 'none';
            }
        } else {
            modeSelectDiv.style.display = 'block';
            singleSetupDiv.style.display = 'none';
            if (mazeGameContainer) mazeGameContainer.style.display = 'none';
            if (multiContainer) multiContainer.style.display = 'none';
            if (mazeConfigPanel) mazeConfigPanel.style.display = 'block';
            updateSavedPanelVisibility();
        }
    }
    if (pageName === 'home') {
        loadPendingRequests();   // 每次回到主页刷新信封未读数
    }
    if (pageName === 'leaderboard') {
        loadLeaderboard();
    }
}

navItems.forEach(item => {
    item.addEventListener('click', () => {
        switchPage(item.dataset.page);
    });
});

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

        const payload = JSON.parse(atob(token.split('.')[1]));
        setUserId(payload.userId);

        showGameHall(username);
    } catch (err) {
        errorMsg.textContent = err.message;
    }
});

logoutBtn.addEventListener('click', async () => {
    try {
        await request('/auth/logout', { method: 'POST' });
    } catch (e) {
        console.warn('登出异常：', e.message);
    } finally {
        if (ws) {
            ws.close();
            ws = null;
        }
        resetMultiUI();
        removeToken();
        removeUsername();
        showLoginPage();
    }
});

toggleLink.addEventListener('click', (e) => {
    e.preventDefault();
    toggleMode();
});

// ========== 模式选择逻辑 ==========
const modeSelectDiv = document.getElementById('mode-select');
const singleSetupDiv = document.getElementById('single-setup');

// ====== 单人迷宫游戏逻辑 ======
let currentMazeData = null;
let grid = [];
let playerPos = { row: 1, col: 0 };
let cellSize = 20;
let gamePaused = false;
let gameWon = false;
let moving = false;

let gameStartTime = null;
let timerInterval = null;

const mazeConfigPanel = document.getElementById('maze-config-panel');
const mazeGameContainer = document.getElementById('maze-game-container');
const mazeCanvas = document.getElementById('maze-canvas');
const ctx = mazeCanvas ? mazeCanvas.getContext('2d') : null;

const bgCanvas = document.getElementById('maze-bg-canvas');
const bgCtx = bgCanvas ? bgCanvas.getContext('2d') : null;

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

        mazeConfigPanel.style.display = 'none';
        mazeGameContainer.style.display = 'block';

        resizeCanvas();
        drawStaticBackground();
        if (ctx) drawPlayer();
        startTimer();
    } catch (e) {
        alert('生成迷宫失败：' + e.message);
    }
});

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
        resetTimer();
    };

    if (currentMazeData && currentMazeData.isSaved === 0 && mazeGameContainer.style.display !== 'none') {
        showExitConfirmation(doBack, doBack);
    } else {
        doBack();
    }
});

function resizeCanvas() {
    if (!grid.length) return;
    const rows = grid.length, cols = grid[0].length;
    const maxWidth = Math.min(window.innerWidth * 0.7, 800);
    const maxHeight = window.innerHeight * 0.6;
    cellSize = Math.min(30, Math.floor(maxWidth / cols), Math.floor(maxHeight / rows));
    mazeCanvas.width = cols * cellSize;
    mazeCanvas.height = rows * cellSize;
    bgCanvas.width = mazeCanvas.width;
    bgCanvas.height = mazeCanvas.height;
}

function drawStaticBackground() {
    if (!bgCtx || !grid.length) return;
    const rows = grid.length, cols = grid[0].length;
    bgCtx.clearRect(0, 0, bgCanvas.width, bgCanvas.height);

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

    const start = { row: currentMazeData.startRow, col: currentMazeData.startCol };
    bgCtx.fillStyle = '#2d5a27';
    bgCtx.fillRect(start.col * cellSize, start.row * cellSize, cellSize, cellSize);
    bgCtx.fillStyle = '#4f8a4b';
    bgCtx.font = `${cellSize * 0.6}px sans-serif`;
    bgCtx.fillText('入', start.col * cellSize + cellSize*0.2, start.row * cellSize + cellSize*0.7);

    const end = { row: currentMazeData.endRow, col: currentMazeData.endCol };
    bgCtx.fillStyle = '#5a2727';
    bgCtx.fillRect(end.col * cellSize, end.row * cellSize, cellSize, cellSize);
    bgCtx.fillStyle = '#c8aa6e';
    bgCtx.fillText('終', end.col * cellSize + cellSize*0.2, end.row * cellSize + cellSize*0.7);

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
            stopTimer();
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
        showExitConfirmation(doNewMaze, doNewMaze);
    } else {
        doNewMaze();
    }
});

window.addEventListener('resize', () => {
    if (mazeGameContainer && mazeGameContainer.style.display === 'block' && currentMazeData) {
        resizeCanvas();
        drawStaticBackground();
        drawPlayer();
    }
});

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
let multiGrid = [];
let multiEndRow = -1, multiEndCol = -1;
let multiPlayersPos = {};
let multiReadySet = new Set();
let multiHostId = null;
let multiStarted = false;
let multiWon = false;
let multiMoving = false;
let multiCellSize = 20;
let multiInRoom = false;
let multiUserIdStr = null;   // 当前 WebSocket 连接绑定的 userId（本标签页专用）

let multiGameStartTime = null;
let multiTimerInterval = null;

let wsSendQueue = [];

const multiContainer = document.getElementById('multi-container');
const roomLobby = document.getElementById('room-lobby');
const waitingRoom = document.getElementById('waiting-room');
const multiGameContainer = document.getElementById('multi-game-container');
const multiCanvas = document.getElementById('multi-canvas');
const multiCtx = multiCanvas ? multiCanvas.getContext('2d') : null;
const multiBgCanvas = document.getElementById('multi-bg-canvas');
const multiBgCtx = multiBgCanvas ? multiBgCanvas.getContext('2d') : null;

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

function connectMultiWS() {
    if (ws) {
        ws.onclose = null;
        ws.close();
        ws = null;
        resetMultiUI();
        wsSendQueue = [];
    }
    const token = getToken();
    ws = new WebSocket(`ws://${location.host}/ws/maze?token=` + token);
    ws.onopen = () => {
        console.log('多人WebSocket已连接');
        // 从当前连接使用的 token 中提取 userId，存入本标签页专用变量
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            multiUserIdStr = String(payload.userId);
        } catch (e) {
            multiUserIdStr = null;
        }
        while (wsSendQueue.length > 0) {
            const msg = wsSendQueue.shift();
            ws.send(JSON.stringify(msg));
        }
    };
    ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        handleWSMessage(msg);
    };
    ws.onclose = () => {
        console.log('多人WebSocket断开');
        multiInRoom = false;
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
        case 'kicked':
            multiInRoom = false;
            alert(data.message || '你被踢出了房间');
            if (ws) { ws.close(); ws = null; }
            resetMultiUI();
            break;
        case 'left':
            multiInRoom = false;
            if (ws) { ws.close(); ws = null; }
            resetMultiUI();
            break;
        case 'error': document.getElementById('room-message').textContent = data; break;
    }
}

function sendWS(type, data) {
    const message = { type, data };
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(message));
    } else {
        wsSendQueue.push(message);
        if (!ws || ws.readyState === WebSocket.CLOSED) {
            connectMultiWS();
        }
    }
}

document.getElementById('multi-mode-btn').addEventListener('click', () => {
    modeSelectDiv.style.display = 'none';
    multiContainer.style.display = 'block';
    roomLobby.style.display = 'block';
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'none';
    // 连接将在 sendWS 中自动建立
});

document.getElementById('back-to-mode-multi').addEventListener('click', () => {
    multiContainer.style.display = 'none';
    modeSelectDiv.style.display = 'block';
    if (multiRoomId) sendWS('leave', { roomId: multiRoomId });
    resetMultiUI();
});

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

function updateRoomInfo(data) {
    multiPlayersPos = data.positions || {};
    multiRoomId = data.roomId;
    multiHostId = data.host;
    multiGrid = data.grid;
    multiEndRow = data.endRow;
    multiEndCol = data.endCol;
    multiStarted = data.started;

    document.getElementById('current-room-id').textContent = multiRoomId;
    const uid = multiUserIdStr;
    document.getElementById('room-host-badge').style.display = (uid && String(multiHostId) === uid) ? 'inline' : 'none';
    if (!uid) {
        document.getElementById('start-game-btn').style.display = 'none';
        return;
    }
    document.getElementById('start-game-btn').style.display =
        (String(multiHostId) === uid) ? 'inline-block' : 'none';

    roomLobby.style.display = 'none';
    waitingRoom.style.display = 'block';

    document.getElementById('kick-target-select').innerHTML = '';
    document.getElementById('player-list').innerHTML = '';
    if (data.players) data.players.forEach(p => addPlayerToList(p));
    if (data.readyPlayers) data.readyPlayers.forEach(p => multiReadySet.add(p));

    updateReadyUI();
    multiInRoom = true;
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
    const uid = multiUserIdStr;
    if (uid && String(multiHostId) === uid && userId !== uid) {
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
        const uid = multiUserIdStr;
        document.getElementById('room-host-badge').style.display = (uid && String(multiHostId) === uid) ? 'inline' : 'none';
        document.getElementById('start-game-btn').style.display = (uid && String(multiHostId) === uid) ? 'inline-block' : 'none';
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
    document.getElementById('start-game-btn').disabled = !(multiReadySet.size >= 2);
}

function startMultiGame(data) {
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'block';
    multiStarted = true;

    if (data.grid) multiGrid = data.grid;
    if (data.endRow !== undefined) multiEndRow = data.endRow;
    if (data.endCol !== undefined) multiEndCol = data.endCol;
    if (data.positions) multiPlayersPos = data.positions;

    document.getElementById('multi-game-status').textContent = '竞速中...';
    initMultiCanvas();
    drawMultiMaze();
    startMultiTimer();
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

    const startRow = 1, startCol = 0;
    multiBgCtx.fillStyle = '#2d5a27';
    multiBgCtx.fillRect(startCol * multiCellSize, startRow * multiCellSize, multiCellSize, multiCellSize);
    multiBgCtx.fillStyle = '#4f8a4b';
    multiBgCtx.font = `${multiCellSize*0.6}px sans-serif`;
    multiBgCtx.fillText('入', startCol * multiCellSize + multiCellSize*0.2, startRow * multiCellSize + multiCellSize*0.7);

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
    const colors = ['#e94560', '#4fc3f7', '#ffb74d', '#81c784'];
    let idx = 0;
    for (const [uid, pos] of Object.entries(multiPlayersPos)) {
        multiCtx.fillStyle = uid === multiUserIdStr ? '#ffd700' : colors[idx % colors.length];
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

    setTimeout(() => {
        multiGameContainer.style.display = 'none';
        waitingRoom.style.display = 'block';
        multiStarted = false;
        multiWon = false;
        resetMultiTimer();
        multiReadySet.clear();
        document.querySelectorAll('#player-list .ready-tag').forEach(tag => {
            tag.textContent = '⏳未准备';
        });
        const uid = multiUserIdStr;
        if (uid && String(multiHostId) === uid) {
            document.getElementById('start-game-btn').style.display = 'inline-block';
            document.getElementById('start-game-btn').disabled = true;
        }
        const readyBtn = document.getElementById('ready-btn');
        if (readyBtn) readyBtn.textContent = '准备';
    }, 1500);
}

async function multiMovePlayer(direction) {
    if (multiMoving || multiWon || !multiStarted) return;
    multiMoving = true;
    sendWS('move', { roomId: multiRoomId, direction });
    multiMoving = false;
}

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
    resetMultiTimer();
    document.getElementById('room-id-input').value = '';
    document.getElementById('room-message').textContent = '';
    document.getElementById('kick-target-select').innerHTML = '';
    multiInRoom = false;
}

window.addEventListener('keydown', (e) => {
    if (!multiGameContainer || multiGameContainer.style.display === 'none') return;
    const key = e.key.toLowerCase();
    if (key === 'w' || key === 'arrowup') { e.preventDefault(); multiMovePlayer('up'); }
    else if (key === 's' || key === 'arrowdown') { e.preventDefault(); multiMovePlayer('down'); }
    else if (key === 'a' || key === 'arrowleft') { e.preventDefault(); multiMovePlayer('left'); }
    else if (key === 'd' || key === 'arrowright') { e.preventDefault(); multiMovePlayer('right'); }
});

document.getElementById('multi-leave-game-btn').addEventListener('click', () => {
    sendWS('leave', { roomId: multiRoomId });
    resetMultiUI();
    roomLobby.style.display = 'block';
    waitingRoom.style.display = 'none';
    multiGameContainer.style.display = 'none';
});

// ========== 我的存档按钮 ==========
document.getElementById('view-saves-btn').addEventListener('click', () => {
    const panel = document.getElementById('saved-mazes-panel');
    panel.style.display = 'block';
    loadSavedMazesList();
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
            currentMazeData.mazeName = mazeName;
        }
        saveModal.style.display = 'none';
        alert('迷宫保存成功！');
        await loadSavedMazesList();
    } catch (e) {
        saveModalError.textContent = e.message;
    }
});

async function loadSavedMazesList() {
    try {
        const result = await request('/maze/saved');
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
        startTimer();
    } catch (e) {
        alert('加载迷宫失败：' + e.message);
    }
}

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

const exitModal = document.getElementById('exit-maze-modal');
const exitSaveBtn = document.getElementById('exit-save-btn');
const exitDiscardBtn = document.getElementById('exit-discard-btn');
const exitCancelBtn = document.getElementById('exit-cancel-btn');
const exitSaveNameInput = document.getElementById('exit-save-name');
const exitModalError = document.getElementById('exit-modal-error');
let pendingExitAction = null;

function showExitConfirmation(onSaveAndExit, onDiscard) {
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
        const action = pendingExitAction;
        hideExitModal();
        if (action && action.onSaveAndExit) {
            action.onSaveAndExit();
        }
    } catch (e) {
        exitModalError.textContent = e.message;
    }
});

exitDiscardBtn.addEventListener('click', () => {
    const action = pendingExitAction;
    hideExitModal();
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

function updateSavedPanelVisibility() {
    const show = (singleSetupDiv.style.display !== 'none' && mazeGameContainer.style.display === 'none');
    if (show) {
        savedMazesPanel.style.display = 'block';
        loadSavedMazesList();
    } else {
        savedMazesPanel.style.display = 'none';
    }
}

const observer = new MutationObserver(() => {
    updateSavedPanelVisibility();
});
observer.observe(mazeGameContainer, { attributes: true, attributeFilter: ['style'] });

window.addEventListener('beforeunload', (e) => {
    if (currentMazeData && currentMazeData.isSaved === 0) {
        e.preventDefault();
        e.returnValue = '您还有未保存的迷宫进度，确定离开吗？';
        return e.returnValue;
    }
});

// ========== 排行榜相关函数 ==========
async function loadLeaderboard() {
    document.getElementById('single-filters').style.display = 'flex';
    document.getElementById('single-table-wrapper').style.display = 'block';
    document.getElementById('multi-table-wrapper').style.display = 'none';
    document.getElementById('my-rank-card').style.display = 'block';
    document.getElementById('multi-my-rank-card').style.display = 'none';

    const algo = document.getElementById('lb-algorithm').value;
    const rows = document.getElementById('lb-rows').value;
    const cols = document.getElementById('lb-cols').value;
    const url = `/maze/leaderboard?algorithm=${algo}&rows=${rows}&cols=${cols}`;

    try {
        const result = await request(url);
        const { topList, myRank } = result.data;

        const tbody = document.getElementById('leaderboard-tbody');
        tbody.innerHTML = '';
        if (!topList || topList.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="no-data">暂无排行记录</td></tr>';
        } else {
            topList.forEach(item => {
                const tr = document.createElement('tr');
                let rankClass = '';
                if (item.rank === 1) rankClass = 'rank-gold';
                else if (item.rank === 2) rankClass = 'rank-silver';
                else if (item.rank === 3) rankClass = 'rank-bronze';

                tr.innerHTML = `
                    <td class="${rankClass}">${item.rank}</td>
                    <td>${item.userId}</td>
                    <td>${item.username}</td>
                    <td>${item.mazeName || '-'}</td>
                    <td>${formatTime(item.elapsedSeconds)}</td>
                    <td>${item.rowsNum}×${item.colsNum}</td>
                    <td>${item.algorithm}</td>
                    <td>${item.savedAt ? new Date(item.savedAt).toLocaleDateString() : '-'}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        const myCard = document.getElementById('my-rank-card');
        const myDetail = document.getElementById('my-rank-detail');
        if (myRank) {
            myCard.style.display = 'block';
            myDetail.innerHTML = `
                <p>排名：<span>${myRank.rank}</span></p>
                <p>用户ID：<span>${myRank.userId}</span></p>
                <p>迷宫名称：<span>${myRank.mazeName || '-'}</span></p>
                <p>用时：<span>${formatTime(myRank.elapsedSeconds)}</span></p>
                <p>尺寸：<span>${myRank.rowsNum}×${myRank.colsNum}</span></p>
                <p>算法：<span>${myRank.algorithm}</span></p>
                <p>通关日期：<span>${myRank.savedAt ? new Date(myRank.savedAt).toLocaleDateString() : '-'}</span></p>
            `;
        } else {
            myCard.style.display = 'block';
            myDetail.innerHTML = '<p>你还没有通关记录，快去挑战吧！</p>';
        }
    } catch (e) {
        console.error('加载排行榜失败:', e);
        document.getElementById('leaderboard-tbody').innerHTML = '<tr><td colspan="8" class="no-data">排行榜加载失败</td></tr>';
    }
}

async function loadMultiLeaderboard() {
    document.getElementById('single-filters').style.display = 'none';
    document.getElementById('single-table-wrapper').style.display = 'none';
    document.getElementById('multi-table-wrapper').style.display = 'block';
    document.getElementById('my-rank-card').style.display = 'none';
    document.getElementById('multi-my-rank-card').style.display = 'block';

    try {
        const result = await request('/maze/multi-leaderboard');
        const { topList, myRank } = result.data;

        const tbody = document.getElementById('multi-leaderboard-tbody');
        tbody.innerHTML = '';
        if (!topList || topList.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="no-data">暂无多人排行记录</td></tr>';
        } else {
            topList.forEach(item => {
                const tr = document.createElement('tr');
                let rankClass = '';
                if (item.rank === 1) rankClass = 'rank-gold';
                else if (item.rank === 2) rankClass = 'rank-silver';
                else if (item.rank === 3) rankClass = 'rank-bronze';
                tr.innerHTML = `
                    <td class="${rankClass}">${item.rank}</td>
                    <td>${item.userId}</td>
                    <td>${item.username}</td>
                    <td>${item.wins}</td>
                    <td>${item.fastestTime ? formatTime(item.fastestTime) : '-'}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        const myCard = document.getElementById('multi-my-rank-card');
        const myDetail = document.getElementById('multi-my-rank-detail');
        if (myRank) {
            myCard.style.display = 'block';
            myDetail.innerHTML = `
                <p>排名：<span>${myRank.rank}</span></p>
                <p>用户ID：<span>${myRank.userId}</span></p>
                <p>获胜次数：<span>${myRank.wins}</span></p>
                <p>最快用时：<span>${myRank.fastestTime ? formatTime(myRank.fastestTime) : '-'}</span></p>
            `;
        } else {
            myCard.style.display = 'block';
            myDetail.innerHTML = '<p>你还没有多人获胜记录，快去对战吧！</p>';
        }
    } catch (e) {
        console.error('加载多人排行榜失败:', e);
        document.getElementById('multi-leaderboard-tbody').innerHTML = '<tr><td colspan="5" class="no-data">排行榜加载失败</td></tr>';
    }
}

document.getElementById('tab-single').addEventListener('click', () => {
    document.getElementById('tab-single').classList.add('active');
    document.getElementById('tab-multi').classList.remove('active');
    loadLeaderboard();
});

document.getElementById('tab-multi').addEventListener('click', () => {
    document.getElementById('tab-multi').classList.add('active');
    document.getElementById('tab-single').classList.remove('active');
    loadMultiLeaderboard();
});

document.getElementById('lb-query-btn').addEventListener('click', loadLeaderboard);
// ========== 玩家实时搜索 ==========
const playerSearchInput = document.getElementById('player-search-input');
const searchResultsDiv = document.getElementById('search-results');
const searchClearBtn = document.getElementById('search-clear');
let searchDebounceTimer;

// 输入事件（防抖 300ms）
playerSearchInput.addEventListener('input', () => {
    clearTimeout(searchDebounceTimer);
    const keyword = playerSearchInput.value.trim();
    if (keyword.length === 0) {
        searchResultsDiv.style.display = 'none';
        if (searchClearBtn) searchClearBtn.style.display = 'none';
        return;
    }
    // 显示清除按钮（如果有）
    if (searchClearBtn) searchClearBtn.style.display = 'block';
    // 防抖请求
    searchDebounceTimer = setTimeout(() => {
        performSearch(keyword);
    }, 300);
});

// 清除按钮（如果有）
if (searchClearBtn) {
    searchClearBtn.addEventListener('click', () => {
        playerSearchInput.value = '';
        searchResultsDiv.style.display = 'none';
        searchClearBtn.style.display = 'none';
        clearTimeout(searchDebounceTimer);
    });
}

async function performSearch(keyword) {
    try {
        const result = await request(`/user/search?keyword=${encodeURIComponent(keyword)}`);
        const users = result.data;
        renderSearchResults(users);
    } catch (e) {
        console.error('搜索失败:', e);
        searchResultsDiv.innerHTML = '<div class="no-data">搜索失败</div>';
        searchResultsDiv.style.display = 'block';
    }
}

function renderSearchResults(users) {
    if (!users || users.length === 0) {
        searchResultsDiv.innerHTML = '<div class="no-data">未找到匹配的玩家</div>';
    } else {
        searchResultsDiv.innerHTML = users.map(user => `
            <div class="player-result-item">
                <div class="player-info" style="flex:1;">
                    <div class="player-name">${user.username}</div>
                    <div class="player-stats">ID: ${user.userId}</div>
                </div>
                ${user.friendStatus === 'FRIEND'
                    ? `<button class="action-btn small add-friend-inline" disabled>已添加</button>`
                    : (user.friendStatus === 'PENDING'
                        ? `<button class="action-btn small add-friend-inline" disabled>等待验证</button>`
                        : `<button class="action-btn small add-friend-inline" onclick="addFriend('${user.userId}', '${user.username}')">+好友</button>`)
                }
            </div>
        `).join('');
    }
    searchResultsDiv.style.display = 'block';
}

async function addFriend(userId, username) {
    try {
        await request('/friend/request', {
            method: 'POST',
            body: JSON.stringify({ targetUserId: Number(userId) })
        });
        alert(`已向 ${username} 发送好友申请`);
        // 刷新搜索状态
        performSearch(document.getElementById('player-search-input').value.trim());
    } catch (e) {
        alert('发送失败：' + e.message);
    }
}

// ========== 好友列表 ==========
async function renderFriendsList() {
    const container = document.getElementById('friends-list');
    try {
        const result = await request('/friend/list');
        const friends = result.data;
        if (!friends || friends.length === 0) {
            container.innerHTML = '<div class="no-friends">暂无好友，快去添加吧~</div>';
            return;
        }
        container.innerHTML = friends.map(f => `
            <div class="friend-item">
                <div class="friend-avatar">👤</div>
                <div class="friend-info">
                    <div class="friend-name">${f.username}</div>
                    <div class="friend-status">
                        <span class="status-dot ${f.online ? 'online' : 'offline'}"></span>
                        ${f.online ? '在线' : '离线'}
                    </div>
                </div>
            </div>
        `).join('');
    } catch (e) {
        console.error('获取好友列表失败', e);
        container.innerHTML = '<div class="no-friends">加载失败，请稍后重试</div>';
    }
}
renderFriendsList();
loadPendingRequests();
initEnvelope();

// ========== 好友申请（信封收件箱） ==========
let requestsExpanded = false;   // 列表是否展开
let pendingCount = 0;           // 未读申请数量

async function loadPendingRequests() {
    try {
        const result = await request('/friend/requests');
        const requests = result.data;
        pendingCount = requests ? requests.length : 0;

        // 更新徽章
        const badge = document.getElementById('envelope-badge');
        if (pendingCount > 0) {
            badge.textContent = pendingCount;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }

        // 如果列表当前是展开状态，重新渲染内容
        if (requestsExpanded) {
            renderRequestsList(requests);
        }
    } catch (e) {
        console.error('获取申请列表失败', e);
    }
}

function renderRequestsList(requests) {
    const container = document.getElementById('friend-requests-list');
    if (!requests || requests.length === 0) {
        container.innerHTML = '<div class="no-data" style="padding:10px;">暂无待处理申请</div>';
        return;
    }
    container.innerHTML = requests.map(req => `
        <div class="friend-request-item">
            <span class="req-username">${req.username} (ID: ${req.userId})</span>
            <div class="req-actions">
                <button class="action-btn small" onclick="handleAccept(${req.userId}, '${req.username}')">同意</button>
                <button class="action-btn small secondary" onclick="handleReject(${req.userId})">拒绝</button>
            </div>
        </div>
    `).join('');
}

function toggleRequests() {
    const list = document.getElementById('friend-requests-list');
    requestsExpanded = !requestsExpanded;
    if (requestsExpanded) {
        list.style.display = 'block';
        // 展开时重新渲染最新数据
        loadPendingRequests();
    } else {
        list.style.display = 'none';
    }
}

async function handleAccept(requestUserId, username) {
    try {
        await request('/friend/accept', {
            method: 'POST',
            body: JSON.stringify({ requestUserId: Number(requestUserId) })
        });
        alert(`已添加 ${username} 为好友`);
        // 刷新申请列表和好友列表
        loadPendingRequests();
        renderFriendsList();
    } catch (e) {
        alert('操作失败：' + e.message);
    }
}

async function handleReject(requestUserId) {
    try {
        await request('/friend/reject', {
            method: 'POST',
            body: JSON.stringify({ requestUserId: Number(requestUserId) })
        });
        loadPendingRequests();
    } catch (e) {
        alert('操作失败：' + e.message);
    }
}

// 绑定信封点击事件（放在初始化中执行，确保DOM已存在）
function initEnvelope() {
    const header = document.getElementById('envelope-header');
    if (header) {
        header.addEventListener('click', toggleRequests);
    }
}