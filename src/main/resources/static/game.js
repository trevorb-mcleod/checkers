/* game.js – Game Hub frontend: Checkers + Battleship */

// ── Global state ──────────────────────────────────────────────────────────────
let gameState      = null;
let currentMode    = null;
let currentGame    = null;
let myColor        = null;

// Legacy polling (kept as safety-net; no longer started anywhere)
let pollTimer      = null;

// WebSocket
let stompClient    = null;
let wsRoomCode     = null;

let bsHorizontal        = true;
let bsLastViewingPlayer = null;

const SHIP_NAMES  = ["Carrier","Battleship","Cruiser","Submarine","Destroyer"];
const SHIP_SIZES  = [5, 4, 3, 3, 2];

// ── Screen helpers ────────────────────────────────────────────────────────
const SCREENS = ['game-select','select','waiting','game','connect4','battleship'];
function showScreen(name) {
    SCREENS.forEach(s =>
        document.getElementById(`screen-${s}`).classList.toggle('hidden', s !== name));
    if (name !== 'waiting') stopPolling();
    if (name === 'game-select' || name === 'select') disconnectWs();
}

// ── API helpers ───────────────────────────────────────────────────────────────
async function apiPost(path, body = {}) {
    const res = await fetch(`/api/room/${path}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    return res.ok ? res.json() : null;
}
async function fetchState() {
    const res = await fetch('/api/room/state');
    const s   = await res.json();
    if (s && s.mode) applyState(s);
    return s;
}
function applyState(s) {
    gameState = s; currentMode = s.mode; currentGame = s.gameType; myColor = s.playerRole;
}

// ── WebSocket (STOMP over SockJS) ─────────────────────────────────────────────
function connectWs(roomCode) {
    if (wsRoomCode === roomCode && stompClient && stompClient.active) return;
    disconnectWs();
    wsRoomCode = roomCode;
    stompClient = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5000,
        onConnect: () => {
            stompClient.subscribe('/topic/room/' + roomCode, async () => {
                const s = await fetchState();
                if (!s || !s.mode) return;
                // Opponent joined while we were on the waiting screen
                if (!document.getElementById('screen-waiting').classList.contains('hidden')) {
                    if (s.roomStatus === 'PLAYING') enterGameScreen(s);
                    return;
                }
                // Normal in-game update
                if (s.gameType === 'BATTLESHIP') renderBattleship(s);
                else if (s.gameType === 'CONNECT4') renderConnect4(s);
                else render(s);
            });
        }
    });
    stompClient.activate();
}
function disconnectWs() {
    if (stompClient) { stompClient.deactivate(); stompClient = null; wsRoomCode = null; }
}

// ── Polling (legacy — kept for safety, no longer started) ────────────────────
function startPolling() {
    stopPolling();
    pollTimer = setInterval(async () => {
        const s = await fetchState();
        if (!s || !s.mode) { stopPolling(); return; }
        if (!document.getElementById('screen-waiting').classList.contains('hidden')) {
            if (s.roomStatus === 'PLAYING') enterGameScreen(s);
        } else {
            if (s.gameType === 'BATTLESHIP') renderBattleship(s);
            else render(s);
        }
        const done = s.status && s.status !== 'PLAYING' && s.status !== 'PLACING';
        if (done) stopPolling();
    }, 2000);
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }

// ── Game selection ────────────────────────────────────────────────────────
let pendingGameType = null;
function selectGame(gameType) {
    pendingGameType = gameType;
    const titles = { CHECKERS: '♟ Checkers', BATTLESHIP: '⚓ Battleship', CONNECT4: '🔴 Connect 4' };
    document.getElementById('mode-select-title').textContent = titles[gameType] || gameType;
    showScreen('select');
    document.getElementById('online-panel').classList.add('hidden');
}

// ── Create / join ─────────────────────────────────────────────────────────────
async function createGame(mode) {
    const gt = pendingGameType || 'CHECKERS';
    const s  = await apiPost('create', { mode, gameType: gt });
    if (!s) return;
    applyState(s);
    if (mode === 'ONLINE' && s.roomStatus === 'WAITING') {
        showWaitingScreen(s.roomCode);
        connectWs(s.roomCode);   // subscribe now — opponent joining will wake us up
    } else {
        enterGameScreen(s);
    }
}
async function joinRoom(code) {
    const res = await fetch('/api/room/join', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code })
    });
    if (!res.ok) { alert('Room not found or full. Check the code and try again.'); showScreen('select'); return; }
    const s = await res.json();
    applyState(s); enterGameScreen(s);
}
function enterGameScreen(s) {
    bsLastViewingPlayer = null;
    stopPolling();
    if (s.mode === 'ONLINE' && s.roomCode) connectWs(s.roomCode);
    if (s.gameType === 'BATTLESHIP') { showScreen('battleship'); renderBattleship(s); }
    else if (s.gameType === 'CONNECT4') { showScreen('connect4'); renderConnect4(s); }
    else { showScreen('game'); render(s); }
}
async function showWaitingScreen(code) {
    document.getElementById('waiting-code').textContent = code;
    let baseUrl = location.origin;
    if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
        try { const r = await fetch('/api/room/host-url'); if (r.ok) { const d = await r.json(); baseUrl = d.url; } } catch(e) {}
    }
    document.getElementById('invite-link-input').value = `${baseUrl}/?join=${code}`;
    showScreen('waiting');
    document.getElementById('screen-waiting').classList.remove('hidden');
}


// ══════════════════════════════════════════════════════════════════════════════
//  CHECKERS
// ══════════════════════════════════════════════════════════════════════════════
async function sendClick(row, col) {
    if (currentMode === 'ONLINE' && myColor !== 'ANY' && gameState && myColor !== gameState.currentPlayer) return;
    const s = await apiPost('click', { row, col });
    if (s) { applyState(s); render(s); }
}
async function startNewGame() {
    const s = await apiPost('new');
    if (!s) return;
    applyState(s);
    bsLastViewingPlayer = null;
    if (s.gameType === 'BATTLESHIP') renderBattleship(s);
    else if (s.gameType === 'CONNECT4') renderConnect4(s);
    else render(s);
}
async function setForcedJumps(enabled) {
    const s = await apiPost('forced-jumps', { enabled });
    if (s) { applyState(s); render(s); }
}
function render(s) {
    if (!s) s = gameState; if (!s) return;
    const { board, selectedRow, selectedCol, validMoves, currentPlayer,
            status, redCount, blackCount, multiJumpPiece, mode, playerRole } = s;
    const badge = document.getElementById('mode-badge');
    if (mode==='CPU') { badge.textContent='🤖 vs CPU'; badge.className='mode-badge cpu-badge'; }
    else if (mode==='ONLINE') { badge.textContent=playerRole==='RED'?'🔴 You are Red':'⚫ You are Black'; badge.className='mode-badge online-badge'; }
    else badge.className='mode-badge hidden';
    const toggle = document.getElementById('forced-jumps-toggle');
    if (toggle) toggle.checked = s.forcedJumps;
    document.getElementById('red-count').textContent   = redCount;
    document.getElementById('black-count').textContent = blackCount;
    const turnEl = document.getElementById('turn-indicator');
    if (status === 'PLAYING') {
        if (multiJumpPiece) {
            turnEl.textContent = `${currentPlayer==='RED'?'Red':'Black'} must jump again!`;
            turnEl.className   = `turn-indicator ${currentPlayer==='RED'?'turn-red':'turn-black'}`;
        } else if (mode==='ONLINE' && playerRole!=='ANY') {
            const mine = currentPlayer===playerRole;
            turnEl.textContent = mine ? '⚡ Your Turn' : "⏳ Opponent's Turn";
            turnEl.className   = mine ? `turn-indicator ${playerRole==='RED'?'turn-red':'turn-black'}` : 'turn-indicator turn-waiting';
        } else {
            turnEl.textContent = currentPlayer==='RED'?"Red's Turn":"Black's Turn";
            turnEl.className   = `turn-indicator ${currentPlayer==='RED'?'turn-red':'turn-black'}`;
        }
    } else {
        turnEl.textContent = status==='RED_WINS'?'🏆 Red Wins!':'🏆 Black Wins!';
        turnEl.className   = 'turn-indicator game-over';
        stopPolling();
    }
    const moveSet = new Set((validMoves||[]).map(m=>`${m[0]},${m[1]}`));
    const boardEl = document.getElementById('board');
    boardEl.innerHTML = '';
    for (let row=0; row<8; row++) {
        for (let col=0; col<8; col++) {
            const isDark=(row+col)%2===1, isSelected=row===selectedRow&&col===selectedCol;
            const isValid=moveSet.has(`${row},${col}`), pv=board[row][col];
            const cell=document.createElement('div');
            cell.className=`cell ${isDark?'dark':'light'}`;
            if (isSelected) cell.classList.add('selected');
            if (isValid)    cell.classList.add('valid-move');
            if (pv!==0) {
                const isRed=pv===1||pv===2, isKing=pv===2||pv===4;
                const p=document.createElement('div');
                p.className=`piece ${isRed?'red-piece':'black-piece'}${isKing?' king':''}`;
                if (isKing) { const c=document.createElement('span'); c.className='king-crown'; c.textContent='♛'; p.appendChild(c); }
                cell.appendChild(p);
            } else if (isValid) { const d=document.createElement('div'); d.className='move-dot'; cell.appendChild(d); }
            cell.addEventListener('click',()=>sendClick(row,col));
            boardEl.appendChild(cell);
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CONNECT FOUR
// ══════════════════════════════════════════════════════════════════════════════
async function c4Drop(col) {
    if (currentMode === 'ONLINE' && myColor !== 'ANY' && gameState && myColor !== gameState.currentPlayer) return;
    const s = await apiPost('connect4/drop', { col });
    if (s) { applyState(s); renderConnect4(s); }
}
function renderConnect4(s) {
    if (!s) s = gameState; if (!s) return;
    const { board, currentPlayer, status, mode, playerRole } = s;

    const badge = document.getElementById('c4-mode-badge');
    if (mode==='CPU') { badge.textContent='🤖 vs CPU'; badge.className='mode-badge cpu-badge'; }
    else if (mode==='ONLINE') { badge.textContent=playerRole==='RED'?'🔴 You are Red':'⚫ You are Black'; badge.className='mode-badge online-badge'; }
    else badge.className='mode-badge hidden';

    const turnEl = document.getElementById('c4-turn-indicator');
    if (status === 'PLAYING') {
        if (mode==='ONLINE' && playerRole!=='ANY') {
            const mine = currentPlayer===playerRole;
            turnEl.textContent = mine ? '⚡ Your Turn' : "⏳ Opponent's Turn";
            turnEl.className   = mine ? `turn-indicator ${playerRole==='RED'?'turn-red':'turn-black'}` : 'turn-indicator turn-waiting';
        } else {
            turnEl.textContent = currentPlayer==='RED'?"Red's Turn":"Black's Turn";
            turnEl.className   = `turn-indicator ${currentPlayer==='RED'?'turn-red':'turn-black'}`;
        }
    } else {
        turnEl.textContent = status==='RED_WINS'?'🏆 Red Wins!':'🏆 Black Wins!';
        turnEl.className   = 'turn-indicator game-over';
        stopPolling();
    }

    // Render the board: 6 rows × 7 columns
    const boardEl = document.getElementById('c4-board');
    boardEl.innerHTML = '';
    const winCells = new Set((s.c4WinCells||[]).map(cell => `${cell[0]},${cell[1]}`));

    for (let row=0; row<6; row++) {
        for (let col=0; col<7; col++) {
            const cell = document.createElement('div');
            cell.className = 'c4-cell';
            const piece = board[row][col];

            if (piece === 0) {
                // Empty cell
                cell.classList.add('c4-empty');
            } else {
                // Piece present
                const pieceEl = document.createElement('div');
                pieceEl.className = `c4-piece ${piece === 1 ? 'c4-red' : 'c4-black'}`;
                if (winCells.has(`${row},${col}`)) {
                    pieceEl.classList.add('c4-winner');
                }
                cell.appendChild(pieceEl);
            }

            boardEl.appendChild(cell);
        }
    }

    // Column drop buttons
    const colButtonsEl = document.getElementById('c4-col-buttons');
    if (colButtonsEl) {
        colButtonsEl.innerHTML = '';
        for (let col=0; col<7; col++) {
            const btn = document.createElement('button');
            btn.className = 'c4-col-btn';
            btn.textContent = '⬇';
            btn.addEventListener('click', () => c4Drop(col));
            colButtonsEl.appendChild(btn);
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  BATTLESHIP
// ══════════════════════════════════════════════════════════════════════════════
async function bsPlaceShip(row, col) {
    const s = await apiPost('battleship/place', { row, col, horizontal: bsHorizontal });
    if (!s) return;
    applyState(s);
    if (s.mode==='LOCAL' && bsLastViewingPlayer && bsLastViewingPlayer !== s.viewingPlayer) {
        showPassAndPlay(s.viewingPlayer, s.lastResult, () => { bsLastViewingPlayer=s.viewingPlayer; renderBattleship(s); });
        return;
    }
    bsLastViewingPlayer = s.viewingPlayer;
    renderBattleship(s);
}
async function bsShoot(row, col) {
    if (currentMode==='ONLINE' && myColor!=='ANY' && gameState && myColor!==gameState.currentPlayer) return;
    const s = await apiPost('battleship/shoot', { row, col });
    if (!s) return;
    applyState(s);
    if (s.mode==='LOCAL' && s.status==='PLAYING' && bsLastViewingPlayer && bsLastViewingPlayer !== s.viewingPlayer) {
        showPassAndPlay(s.viewingPlayer, s.lastResult, () => { bsLastViewingPlayer=s.viewingPlayer; renderBattleship(s); });
        return;
    }
    bsLastViewingPlayer = s.viewingPlayer;
    renderBattleship(s);
}
function renderBattleship(s) {
    if (!s) return;
    const isPlacing = s.status==='PLACING', isOver = s.status==='RED_WINS'||s.status==='BLACK_WINS';
    document.getElementById('bs-placing-panel').classList.toggle('hidden', !isPlacing);
    document.getElementById('bs-battle-panel').classList.toggle('hidden',  isPlacing && !isOver);
    document.getElementById('bs-new-game-btn').classList.toggle('hidden',  !isOver);
    const badge = document.getElementById('bs-mode-badge');
    if (s.mode==='CPU') { badge.textContent='🤖 vs CPU'; badge.className='mode-badge cpu-badge'; }
    else if (s.mode==='ONLINE') { badge.textContent=s.playerRole==='RED'?'🔴 You are Red':'⚫ You are Black'; badge.className='mode-badge online-badge'; }
    else badge.className='mode-badge hidden';
    if (isPlacing) {
        document.getElementById('bs-placing-title').textContent =
            `${s.viewingPlayer==='RED'?'🔴 Red':'⚫ Black'} — Place your ships`;
        renderShipList(s.myShipsPlaced);
        document.getElementById('bs-enemy-wrapper').classList.add('locked');
    } else {
        document.getElementById('bs-enemy-wrapper').classList.remove('locked');
    }
    document.getElementById('bs-my-label').textContent = s.viewingPlayer==='RED'?'🔴 Your Fleet':'⚫ Your Fleet';
    const turnEl = document.getElementById('bs-turn-indicator');
    if (!isPlacing) {
        if (isOver) {
            turnEl.textContent = s.status==='RED_WINS'?'🏆 Red Wins!':'🏆 Black Wins!';
            turnEl.className   = 'turn-indicator game-over';
            document.getElementById('bs-battle-panel').classList.remove('hidden');
            stopPolling();
        } else if (s.mode==='ONLINE' && s.playerRole!=='ANY') {
            const mine = s.currentPlayer===s.playerRole;
            turnEl.textContent = mine?'⚡ Your Turn — Fire!':"⏳ Opponent's Turn";
            turnEl.className   = mine?`turn-indicator ${s.playerRole==='RED'?'turn-red':'turn-black'}`:'turn-indicator turn-waiting';
        } else {
            turnEl.textContent = `${s.currentPlayer==='RED'?'Red':'Black'}'s Turn — Fire!`;
            turnEl.className   = `turn-indicator ${s.currentPlayer==='RED'?'turn-red':'turn-black'}`;
        }
    }
    buildBsGrid(document.getElementById('bs-my-grid'),    s.myGrid,    'my',    isPlacing, s);
    buildBsGrid(document.getElementById('bs-enemy-grid'), s.enemyGrid, 'enemy', isPlacing, s);
}
function renderShipList(placed) {
    const el = document.getElementById('bs-ship-list');
    el.innerHTML = '';
    SHIP_NAMES.forEach((name, i) => {
        const pill = document.createElement('div');
        pill.className = 'bs-ship-pill' + (i<placed?' placed':i===placed?' active':'');
        pill.textContent = `${name} (${SHIP_SIZES[i]})`;
        el.appendChild(pill);
    });
}
function buildBsGrid(container, grid, type, isPlacing, s) {
    container.innerHTML = '';
    for (let r=0; r<10; r++) {
        for (let c=0; c<10; c++) {
            const cell = document.createElement('div');
            cell.className = 'bs-cell';
            const v = grid[r][c];
            if (type==='my') {
                if (v > 0 && v <= 5) cell.classList.add('ship');
                if (v < 0)           cell.classList.add('ship-hit');
                if (v === 6)         cell.classList.add('miss-on-me');
                if (isPlacing) {
                    cell.addEventListener('mouseenter', ()=>previewShip(r,c,s.myShipsPlaced));
                    cell.addEventListener('mouseleave', clearPreview);
                    cell.addEventListener('click',      ()=>bsPlaceShip(r,c));
                }
            } else {
                if (v===1) cell.classList.add('miss');
                else if (v===2) cell.classList.add('hit');
                else if (v===3) cell.classList.add('sunk');
                else cell.classList.add('unknown');
                if (!isPlacing && s.status==='PLAYING' && v===0)
                    cell.addEventListener('click', ()=>bsShoot(r,c));
            }
            container.appendChild(cell);
        }
    }
}
function previewShip(row, col, shipIndex) {
    if (shipIndex >= SHIP_SIZES.length) return;
    clearPreview();
    const size  = SHIP_SIZES[shipIndex];
    const cells = document.getElementById('bs-my-grid').querySelectorAll('.bs-cell');
    for (let i=0; i<size; i++) {
        const r = bsHorizontal ? row : row+i, c = bsHorizontal ? col+i : col;
        if (r<0||r>=10||c<0||c>=10) continue;
        const cell = cells[r*10+c];
        if (!cell) continue;
        cell.classList.add(cell.classList.contains('ship') ? 'preview-invalid' : 'preview-valid');
    }
}
function clearPreview() {
    document.querySelectorAll('.preview-valid,.preview-invalid')
        .forEach(el=>el.classList.remove('preview-valid','preview-invalid'));
}

// ── Pass-and-play overlay ─────────────────────────────────────────────────────
function showPassAndPlay(nextPlayer, lastResult, onReady) {
    const overlay = document.getElementById('pass-overlay');
    document.getElementById('pass-title').textContent  = `Hand device to ${nextPlayer==='RED'?'Red':'Black'} Player`;
    document.getElementById('pass-result').textContent = formatResult(lastResult);
    overlay.classList.remove('hidden');
    const btn = document.getElementById('pass-ready-btn');
    const h = () => { btn.removeEventListener('click',h); overlay.classList.add('hidden'); onReady(); };
    btn.addEventListener('click', h);
}
function formatResult(r) {
    if (!r) return '';
    if (r==='HIT')  return '💥 Hit!';
    if (r==='MISS') return '💧 Miss — hand it over!';
    if (r.startsWith('SUNK:')) return `💀 Sunk the ${r.slice(5)}!`;
    if (r.startsWith('WIN:'))  return `🏆 Sunk the ${r.slice(4)}! Game over!`;
    return r;
}

// ══════════════════════════════════════════════════════════════════════════════
//  BOOTSTRAP
// ══════════════════════════════════════════════════════════════════════════════
window.addEventListener('DOMContentLoaded', async () => {
    document.getElementById('btn-game-checkers')  .addEventListener('click', ()=>selectGame('CHECKERS'));
    document.getElementById('btn-game-battleship').addEventListener('click', ()=>selectGame('BATTLESHIP'));
    document.getElementById('btn-game-connect4')  .addEventListener('click', ()=>selectGame('CONNECT4'));
    document.getElementById('btn-back-game-select').addEventListener('click', ()=>{
        showScreen('game-select'); document.getElementById('online-panel').classList.add('hidden');
    });
    document.getElementById('btn-cpu')  .addEventListener('click', ()=>createGame('CPU'));
    document.getElementById('btn-local').addEventListener('click', ()=>createGame('LOCAL'));
    document.getElementById('btn-online').addEventListener('click', ()=>
        document.getElementById('online-panel').classList.remove('hidden'));
    document.getElementById('btn-online-back').addEventListener('click', ()=>
        document.getElementById('online-panel').classList.add('hidden'));
    document.getElementById('btn-create').addEventListener('click', ()=>createGame('ONLINE'));
    document.getElementById('btn-join-code').addEventListener('click', ()=>{
        const code = document.getElementById('join-code-input').value.trim();
        if (code.length===4) joinRoom(code); else alert('Please enter a 4-digit code.');
    });
    document.getElementById('join-code-input').addEventListener('keydown', e=>{
        if (e.key==='Enter') document.getElementById('btn-join-code').click();
    });
    document.getElementById('btn-copy-link').addEventListener('click', ()=>{
        const link = document.getElementById('invite-link-input').value;
        navigator.clipboard.writeText(link).then(()=>{
            const fb=document.getElementById('copy-feedback');
            fb.classList.remove('hidden'); setTimeout(()=>fb.classList.add('hidden'),2000);
        });
    });
    document.getElementById('btn-cancel-wait').addEventListener('click', ()=>{
        disconnectWs(); stopPolling(); showScreen('select');
        document.getElementById('online-panel').classList.remove('hidden');
    });
    document.getElementById('btn-back-menu').addEventListener('click', ()=>{
        disconnectWs(); stopPolling();
        document.getElementById('online-panel').classList.add('hidden'); showScreen('game-select');
    });
    document.getElementById('new-game-btn').addEventListener('click', startNewGame);
    document.getElementById('forced-jumps-toggle').addEventListener('change', e=>setForcedJumps(e.target.checked));
    document.getElementById('btn-bs-back-menu').addEventListener('click', ()=>{ disconnectWs(); stopPolling(); showScreen('game-select'); });
    document.getElementById('bs-new-game-btn').addEventListener('click', startNewGame);
    document.getElementById('bs-rotate-btn').addEventListener('click', ()=>{
        bsHorizontal = !bsHorizontal;
        document.getElementById('bs-rotate-btn').textContent =
            `🔄 Rotate (${bsHorizontal?'Horizontal':'Vertical'})`;
    });
    document.getElementById('btn-c4-back-menu').addEventListener('click', ()=>{ disconnectWs(); stopPolling(); showScreen('game-select'); });
    document.getElementById('c4-new-game-btn').addEventListener('click', startNewGame);

    // Check ?join=CODE in URL
    const params = new URLSearchParams(location.search);
    const joinCode = params.get('join');
    if (joinCode) { history.replaceState(null,'','/'); await joinRoom(joinCode); return; }

    // Restore existing session
    const s = await fetchState();
    if (s && s.mode) {
        if (s.roomStatus === 'WAITING') { showWaitingScreen(s.roomCode); connectWs(s.roomCode); }
        else { enterGameScreen(s); }
    } else {
        showScreen('game-select');
    }
});
