/* game.js – Checkers frontend (room-based multi-mode) */

// ── State ──────────────────────────────────────────────────────────────────

let gameState   = null;
let currentMode = null;   // "LOCAL" | "CPU" | "ONLINE"
let myColor     = null;   // "ANY" | "RED" | "BLACK"
let pollTimer   = null;

// ── Screen management ──────────────────────────────────────────────────────

function showScreen(name) {
    ['select', 'waiting', 'game'].forEach(s => {
        document.getElementById(`screen-${s}`).classList.toggle('hidden', s !== name);
    });
    if (name !== 'waiting') stopPolling();
}

// ── Room API ───────────────────────────────────────────────────────────────

async function apiPost(path, body = {}) {
    const res = await fetch(`/api/room/${path}`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify(body)
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
    gameState   = s;
    currentMode = s.mode;
    myColor     = s.playerRole;
}

// ── Create / join ──────────────────────────────────────────────────────────

async function createGame(mode) {
    const s = await apiPost('create', { mode });
    if (!s) return;
    applyState(s);

    if (mode === 'ONLINE' && s.roomStatus === 'WAITING') {
        showWaitingScreen(s.roomCode);
        startPolling();
    } else {
        showScreen('game');
        render();
        if (mode === 'ONLINE') startPolling();
    }
}

async function joinRoom(code) {
    const res = await fetch('/api/room/join', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ code })
    });
    if (!res.ok) {
        alert('Room not found or already full. Check the code and try again.');
        showScreen('select');
        return;
    }
    const s = await res.json();
    applyState(s);
    showScreen('game');
    render();
    startPolling();   // poll for opponent's moves
}

async function showWaitingScreen(code) {
    document.getElementById('waiting-code').textContent = code;

    // On cloud deployments location.origin is already the public URL.
    // Only replace it when the host is using localhost (local dev), in which case
    // we ask the server for its LAN IP so other devices on the same network can connect.
    let baseUrl = location.origin;
    if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
        try {
            const res = await fetch('/api/room/host-url');
            if (res.ok) {
                const data = await res.json();
                baseUrl = data.url;
            }
        } catch (e) { /* keep location.origin */ }
    }

    const link = `${baseUrl}/?join=${code}`;
    document.getElementById('invite-link-input').value = link;
    showScreen('waiting');
    document.getElementById('screen-waiting').classList.remove('hidden');
}

// ── Polling ────────────────────────────────────────────────────────────────

function startPolling() {
    stopPolling();
    pollTimer = setInterval(async () => {
        const s = await fetchState();
        if (!s || !s.mode) { stopPolling(); return; }

        // Waiting screen → switch to game when opponent joins
        if (!document.getElementById('screen-waiting').classList.contains('hidden')) {
            if (s.roomStatus === 'PLAYING') {
                showScreen('game');
                render();
            }
        } else {
            render();
        }

        // Stop polling once game is over
        if (s.status && s.status !== 'PLAYING') stopPolling();
    }, 2000);
}

function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

// ── In-game API ────────────────────────────────────────────────────────────

async function sendClick(row, col) {
    // Click guard: in ONLINE mode ignore clicks when it's not our turn
    if (currentMode === 'ONLINE' && myColor !== 'ANY'
            && gameState && myColor !== gameState.currentPlayer) return;

    const s = await apiPost('click', { row, col });
    if (s) { applyState(s); render(); }
}

async function startNewGame() {
    const s = await apiPost('new');
    if (s) { applyState(s); render(); }
}

async function setForcedJumps(enabled) {
    const s = await apiPost('forced-jumps', { enabled });
    if (s) { applyState(s); render(); }
}

// ── Rendering ──────────────────────────────────────────────────────────────

function render() {
    if (!gameState) return;

    const { board, selectedRow, selectedCol, validMoves,
            currentPlayer, status, redCount, blackCount,
            multiJumpPiece, mode, playerRole } = gameState;

    // ---- Mode badge ----
    const badge = document.getElementById('mode-badge');
    if (mode === 'CPU') {
        badge.textContent = '🤖 vs CPU';
        badge.className   = 'mode-badge cpu-badge';
    } else if (mode === 'ONLINE') {
        badge.textContent = playerRole === 'RED' ? '🔴 You are Red' : '⚫ You are Black';
        badge.className   = 'mode-badge online-badge';
    } else {
        badge.className = 'mode-badge hidden';
    }

    // ---- Toggle sync ----
    const toggle = document.getElementById('forced-jumps-toggle');
    if (toggle) toggle.checked = gameState.forcedJumps;

    // ---- Counters ----
    document.getElementById('red-count').textContent   = redCount;
    document.getElementById('black-count').textContent = blackCount;

    // ---- Turn indicator ----
    const turnEl = document.getElementById('turn-indicator');
    if (status === 'PLAYING') {
        if (multiJumpPiece) {
            const name = currentPlayer === 'RED' ? 'Red' : 'Black';
            turnEl.textContent = `${name} must jump again!`;
            turnEl.className   = `turn-indicator ${currentPlayer === 'RED' ? 'turn-red' : 'turn-black'}`;
        } else if (mode === 'ONLINE' && playerRole !== 'ANY') {
            const isMyTurn = currentPlayer === playerRole;
            turnEl.textContent = isMyTurn ? '⚡ Your Turn' : "⏳ Opponent's Turn";
            turnEl.className   = isMyTurn
                ? `turn-indicator ${playerRole === 'RED' ? 'turn-red' : 'turn-black'}`
                : 'turn-indicator turn-waiting';
        } else {
            turnEl.textContent = currentPlayer === 'RED' ? "Red's Turn" : "Black's Turn";
            turnEl.className   = `turn-indicator ${currentPlayer === 'RED' ? 'turn-red' : 'turn-black'}`;
        }
    } else {
        turnEl.textContent = status === 'RED_WINS' ? '🏆 Red Wins!' : '🏆 Black Wins!';
        turnEl.className   = 'turn-indicator game-over';
        stopPolling();
    }

    // ---- Board ----
    const moveSet = new Set((validMoves || []).map(m => `${m[0]},${m[1]}`));
    const boardEl = document.getElementById('board');
    boardEl.innerHTML = '';

    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {
            const isDark      = (row + col) % 2 === 1;
            const isSelected  = row === selectedRow && col === selectedCol;
            const isValidMove = moveSet.has(`${row},${col}`);
            const pieceValue  = board[row][col];

            const cell = document.createElement('div');
            cell.className = `cell ${isDark ? 'dark' : 'light'}`;
            if (isSelected)  cell.classList.add('selected');
            if (isValidMove) cell.classList.add('valid-move');

            if (pieceValue !== 0) {
                const isRed  = pieceValue === 1 || pieceValue === 2;
                const isKing = pieceValue === 2 || pieceValue === 4;

                const pieceEl = document.createElement('div');
                pieceEl.className = `piece ${isRed ? 'red-piece' : 'black-piece'}${isKing ? ' king' : ''}`;

                if (isKing) {
                    const crown = document.createElement('span');
                    crown.className   = 'king-crown';
                    crown.textContent = '♛';
                    pieceEl.appendChild(crown);
                }
                cell.appendChild(pieceEl);
            } else if (isValidMove) {
                const dot = document.createElement('div');
                dot.className = 'move-dot';
                cell.appendChild(dot);
            }

            cell.addEventListener('click', () => sendClick(row, col));
            boardEl.appendChild(cell);
        }
    }
}

// ── Bootstrap ──────────────────────────────────────────────────────────────

window.addEventListener('DOMContentLoaded', async () => {
    // ---- Attach mode-select listeners ----
    document.getElementById('btn-cpu')  .addEventListener('click', () => createGame('CPU'));
    document.getElementById('btn-local').addEventListener('click', () => createGame('LOCAL'));
    document.getElementById('btn-online').addEventListener('click', () => {
        document.getElementById('online-panel').classList.remove('hidden');
    });
    document.getElementById('btn-online-back').addEventListener('click', () => {
        document.getElementById('online-panel').classList.add('hidden');
    });
    document.getElementById('btn-create').addEventListener('click', () => createGame('ONLINE'));
    document.getElementById('btn-join-code').addEventListener('click', () => {
        const code = document.getElementById('join-code-input').value.trim();
        if (code.length === 4) joinRoom(code);
        else alert('Please enter a 4-digit code.');
    });
    document.getElementById('join-code-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') document.getElementById('btn-join-code').click();
    });

    // ---- Waiting-screen actions ----
    document.getElementById('btn-copy-link').addEventListener('click', () => {
        const link = document.getElementById('invite-link-input').value;
        navigator.clipboard.writeText(link).then(() => {
            const fb = document.getElementById('copy-feedback');
            fb.classList.remove('hidden');
            setTimeout(() => fb.classList.add('hidden'), 2000);
        });
    });
    document.getElementById('btn-cancel-wait').addEventListener('click', () => {
        stopPolling();
        showScreen('select');
        document.getElementById('online-panel').classList.remove('hidden');
    });

    // ---- Game-screen actions ----
    document.getElementById('btn-back-menu').addEventListener('click', () => {
        stopPolling();
        document.getElementById('online-panel').classList.add('hidden');
        showScreen('select');
    });
    document.getElementById('new-game-btn').addEventListener('click', startNewGame);
    document.getElementById('forced-jumps-toggle').addEventListener('change',
        e => setForcedJumps(e.target.checked));

    // ---- Check for ?join=CODE in URL ----
    const params   = new URLSearchParams(location.search);
    const joinCode = params.get('join');
    if (joinCode) {
        // Clean URL without navigating away
        history.replaceState(null, '', '/');
        await joinRoom(joinCode);
        return;
    }

    // ---- Restore existing session ----
    const s = await fetchState();
    if (s && s.mode) {
        if (s.roomStatus === 'WAITING') {
            showWaitingScreen(s.roomCode);
            startPolling();
        } else {
            showScreen('game');
            render();
            if (s.mode === 'ONLINE') startPolling();
        }
    } else {
        showScreen('select');
    }
});
