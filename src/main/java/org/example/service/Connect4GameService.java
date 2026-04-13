package org.example.service;

import org.example.model.GameState;

import java.util.*;

/**
 * Connect Four game logic.  6 rows × 7 columns.
 * Board values: 0=empty  1=RED  2=BLACK
 * Status: PLAYING | RED_WINS | BLACK_WINS | DRAW
 */
public class Connect4GameService {

    public static final int ROWS = 6;
    public static final int COLS = 7;

    private int[][] board = new int[ROWS][COLS];
    private String currentPlayer = "RED";
    private String status = "PLAYING";
    private int lastCol = -1;
    private List<int[]> winCells = null;

    public Connect4GameService() { newGame(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void newGame() {
        board = new int[ROWS][COLS];
        currentPlayer = "RED";
        status = "PLAYING";
        lastCol = -1;
        winCells = null;
    }

    // ── Drop ──────────────────────────────────────────────────────────────────

    /**
     * Drop a piece into the given column for the given player.
     * Returns true on success, false if invalid (wrong turn, full column, game over).
     */
    public synchronized boolean dropPiece(String player, int col) {
        if (!"PLAYING".equals(status)) return false;
        if (!player.equals(currentPlayer)) return false;
        if (col < 0 || col >= COLS) return false;

        // Find the lowest empty row in the column
        int row = -1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == 0) { row = r; break; }
        }
        if (row == -1) return false; // column full

        board[row][col] = "RED".equals(player) ? 1 : 2;
        lastCol = col;

        List<int[]> winning = findWin(row, col);
        if (winning != null) {
            winCells = winning;
            status = "RED".equals(player) ? "RED_WINS" : "BLACK_WINS";
        } else if (isBoardFull()) {
            status = "DRAW";
        } else {
            currentPlayer = "RED".equals(currentPlayer) ? "BLACK" : "RED";
        }
        return true;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public synchronized GameState getState() {
        GameState gs = new GameState();
        int[][] copy = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) copy[r] = board[r].clone();
        gs.setBoard(copy);
        gs.setCurrentPlayer(currentPlayer);
        gs.setStatus(status);
        gs.setC4LastCol(lastCol);
        gs.setC4WinCells(winCells != null ? new ArrayList<>(winCells) : null);
        return gs;
    }

    // ── AI helpers ────────────────────────────────────────────────────────────

    public synchronized Connect4GameService copy() {
        Connect4GameService c = new Connect4GameService();
        c.board = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) c.board[r] = board[r].clone();
        c.currentPlayer = currentPlayer;
        c.status = status;
        c.lastCol = lastCol;
        c.winCells = null;
        return c;
    }

    public synchronized List<Integer> getValidColumns() {
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < COLS; c++) if (board[0][c] == 0) cols.add(c);
        return cols;
    }

    public synchronized int[][] getBoardDirect() {
        int[][] copy = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) copy[r] = board[r].clone();
        return copy;
    }

    public String getCurrentPlayer() { return currentPlayer; }
    public String getStatus()        { return status; }

    // ── Win detection ─────────────────────────────────────────────────────────

    private List<int[]> findWin(int row, int col) {
        int piece = board[row][col];
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            List<int[]> line = collectLine(row, col, d[0], d[1], piece);
            if (line.size() >= 4) return line.subList(0, 4);
        }
        return null;
    }

    private List<int[]> collectLine(int row, int col, int dr, int dc, int piece) {
        List<int[]> neg = new ArrayList<>();
        for (int i = 1; i < 4; i++) {
            int r = row - dr*i, c = col - dc*i;
            if (inBounds(r,c) && board[r][c] == piece) neg.add(new int[]{r,c});
            else break;
        }
        Collections.reverse(neg);
        List<int[]> result = new ArrayList<>(neg);
        result.add(new int[]{row, col});
        for (int i = 1; i < 4; i++) {
            int r = row + dr*i, c = col + dc*i;
            if (inBounds(r,c) && board[r][c] == piece) result.add(new int[]{r,c});
            else break;
        }
        return result;
    }

    private boolean isBoardFull() {
        for (int c = 0; c < COLS; c++) if (board[0][c] == 0) return false;
        return true;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }
}

