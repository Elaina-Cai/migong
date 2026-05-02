// ========== 配置 ==========
const API_BASE = '';                // 同源请求
const TOKEN_KEY = 'maze_token';
const USERNAME_KEY = 'maze_username';

// ========== 工具函数 ==========
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

// 点击多人模式（暂时弹出提示，后续对接游戏大厅）
document.getElementById('multi-mode-btn').addEventListener('click', () => {
    alert('多人模式即将开放，敬请期待！');
});

// ====== 单人迷宫游戏逻辑 ======
let currentMazeData = null;      // 当前迷宫实体
let grid = [];                  // 二维网格 0=路 1=墙
let playerPos = { row: 1, col: 0 };
let cellSize = 20;
let gamePaused = false;
let gameWon = false;
let moving = false;             // 移动锁，防止请求堆积

// DOM 元素
const mazeConfigPanel = document.getElementById('maze-config-panel');
const mazeGameContainer = document.getElementById('maze-game-container');
const mazeCanvas = document.getElementById('maze-canvas');
const ctx = mazeCanvas ? mazeCanvas.getContext('2d') : null;

// 离屏背景 Canvas
const bgCanvas = document.getElementById('maze-bg-canvas');
const bgCtx = bgCanvas ? bgCanvas.getContext('2d') : null;

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
    } catch (e) {
        alert('生成迷宫失败：' + e.message);
    }
});

// 重写返回按钮逻辑（确保参数面板显示）
document.getElementById('back-to-mode').addEventListener('click', () => {
    document.getElementById('single-setup').style.display = 'none';
    document.getElementById('mode-select').style.display = 'block';
    if (mazeGameContainer) mazeGameContainer.style.display = 'none';
    if (mazeConfigPanel) mazeConfigPanel.style.display = 'block';
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
    mazeGameContainer.style.display = 'none';
    mazeConfigPanel.style.display = 'block';
    currentMazeData = null;
    grid = [];
    gameWon = false;
    gamePaused = false;
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
    modeSelectDiv.style.display = 'none';       // 隐藏模式选择
    singleSetupDiv.style.display = 'block';     // 显示单人设置父容器
    mazeConfigPanel.style.display = 'block';    // 显示参数面板
    mazeGameContainer.style.display = 'none';   // 隐藏游戏画面（如果有残留）
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