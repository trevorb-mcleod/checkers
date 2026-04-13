package org.example.service;

import java.util.*;

/**
 * Battleship game logic.  Two 10×10 grids, 5 ships each.
 * Ships placed in order: Carrier(5), Battleship(4), Cruiser(3), Submarine(3), Destroyer(2).
 *
 * Own-grid values : 0=water  1-5=ship_id (intact)  -(1-5)=ship_id (hit)
 * Shot-grid values: 0=unknown  1=miss  2=hit  3=sunk
 */
public class BattleshipGameService {

    public static final int      GRID        = 10;
    public static final int[]    SHIP_SIZES  = {5, 4, 3, 3, 2};
    public static final String[] SHIP_NAMES  = {"Carrier","Battleship","Cruiser","Submarine","Destroyer"};

    private int[][] redGrid    = new int[GRID][GRID];
    private int[][] blackGrid  = new int[GRID][GRID];
    private int[][] redShots   = new int[GRID][GRID]; // red firing at black
    private int[][] blackShots = new int[GRID][GRID]; // black firing at red

    private int     redPlaced   = 0;   // ships placed by RED  (0–5)
    private int     blackPlaced = 0;   // ships placed by BLACK (0–5)
    private boolean redReady    = false;
    private boolean blackReady  = false;

    private String status        = "PLACING"; // PLACING | PLAYING | RED_WINS | BLACK_WINS
    private String placingTurn   = "RED";     // whose placement turn it is
    private String currentPlayer = "RED";     // who fires next during PLAYING
    private String lastResult    = null;      // last shot result for UI feedback

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public BattleshipGameService() { }

    public synchronized void newGame() {
        redGrid    = new int[GRID][GRID];
        blackGrid  = new int[GRID][GRID];
        redShots   = new int[GRID][GRID];
        blackShots = new int[GRID][GRID];
        redPlaced = blackPlaced = 0;
        redReady = blackReady = false;
        status = "PLACING";
        placingTurn = "RED";
        currentPlayer = "RED";
        lastResult = null;
    }

    // ── Ship placement ────────────────────────────────────────────────────────

    /**
     * Place the next ship for the given player.
     * Ships must be placed sequentially (Carrier first, Destroyer last).
     * Returns true on success.
     */
    public synchronized boolean placeShip(String player, int row, int col, boolean horizontal) {
        return doPlaceShip(player, row, col, horizontal, false);
    }

    /** Bypass placingTurn check — used for CPU auto-placement before game starts. */
    public synchronized boolean placeShipForce(String player, int row, int col, boolean horizontal) {
        return doPlaceShip(player, row, col, horizontal, true);
    }

    private boolean doPlaceShip(String player, int row, int col, boolean horizontal, boolean force) {
        if (!"PLACING".equals(status)) return false;
        if (!force && !player.equals(placingTurn)) return false;

        int placed = "RED".equals(player) ? redPlaced : blackPlaced;
        if (placed >= SHIP_SIZES.length) return false;

        int     shipId = placed + 1;
        int     size   = SHIP_SIZES[placed];
        int[][] grid   = "RED".equals(player) ? redGrid : blackGrid;

        List<int[]> cells = shipCells(row, col, size, horizontal);
        if (cells == null) return false;
        for (int[] c : cells) {
            if (c[0] < 0 || c[0] >= GRID || c[1] < 0 || c[1] >= GRID) return false;
            if (grid[c[0]][c[1]] != 0) return false; // collision
        }

        for (int[] c : cells) grid[c[0]][c[1]] = shipId;

        if ("RED".equals(player)) {
            redPlaced++;
            if (redPlaced == SHIP_SIZES.length && !force) {
                redReady = true;
                if (!blackReady) placingTurn = "BLACK";
            }
        } else {
            blackPlaced++;
            if (blackPlaced == SHIP_SIZES.length) blackReady = true;
        }

        if (redReady && blackReady) {
            status = "PLAYING";
            currentPlayer = "RED";
        }
        return true;
    }

    /** Mark RED as done placing (used in CPU mode after RED finishes). */
    public synchronized void markRedReady() {
        redReady = true;
        if (blackReady) { status = "PLAYING"; currentPlayer = "RED"; }
    }

    // ── Shooting ──────────────────────────────────────────────────────────────

    /**
     * Fire at (row, col) as the given player.
     * Returns: "HIT", "MISS", "SUNK:ShipName", "WIN:ShipName", or null if invalid.
     */
    public synchronized String shoot(String player, int row, int col) {
        if (!"PLAYING".equals(status)) return null;
        if (!player.equals(currentPlayer)) return null;
        if (row < 0 || row >= GRID || col < 0 || col >= GRID) return null;

        int[][] targetGrid = "RED".equals(player) ? blackGrid : redGrid;
        int[][] shotGrid   = "RED".equals(player) ? redShots  : blackShots;

        if (shotGrid[row][col] != 0) return null; // already shot here

        int cell = targetGrid[row][col];
        String result;

        if (cell > 0) {
            int shipId = cell;
            targetGrid[row][col] = -shipId;
            shotGrid[row][col]   = 2; // hit

            if (isShipSunk(targetGrid, shipId)) {
                revealSunkInShots(targetGrid, shotGrid, shipId);
                result = "SUNK:" + SHIP_NAMES[shipId - 1];
                if (allSunk(targetGrid)) {
                    status = "RED".equals(player) ? "RED_WINS" : "BLACK_WINS";
                    result = "WIN:" + SHIP_NAMES[shipId - 1];
                }
            } else {
                result = "HIT";
            }
            // Hit → same player continues (unless game over)
        } else {
            shotGrid[row][col] = 1; // miss
            result = "MISS";
            currentPlayer = "RED".equals(currentPlayer) ? "BLACK" : "RED";
        }

        lastResult = result;
        return result;
    }

    // ── State snapshot ────────────────────────────────────────────────────────

    /** Returns the raw state maps needed to build a response. */
    public synchronized BattleshipSnapshot getSnapshot(String playerRole) {
        // LOCAL ("ANY") → show whoever is currently active
        String viewing;
        if ("ANY".equals(playerRole)) {
            viewing = "PLAYING".equals(status) ? currentPlayer : placingTurn;
        } else {
            viewing = playerRole;
        }

        boolean isRed = "RED".equals(viewing);

        BattleshipSnapshot snap = new BattleshipSnapshot();
        snap.myGrid        = copy2d(isRed ? redGrid   : blackGrid);
        snap.enemyGrid     = copy2d(isRed ? redShots  : blackShots);
        snap.myShipsPlaced = isRed ? redPlaced : blackPlaced;
        snap.viewingPlayer = viewing;
        snap.status        = status;
        snap.currentPlayer = currentPlayer;
        snap.placingTurn   = placingTurn;
        snap.lastResult    = lastResult;
        snap.redReady      = redReady;
        snap.blackReady    = blackReady;

        // Overlay enemy misses onto own grid so the frontend can visualise them.
        // Value 6 = "enemy shot here and missed" (ship IDs only go up to 5).
        int[][] enemyShots = isRed ? blackShots : redShots;
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                if (enemyShots[r][c] == 1 && snap.myGrid[r][c] == 0)
                    snap.myGrid[r][c] = 6;

        return snap;
    }

    // ── AI helpers ────────────────────────────────────────────────────────────

    public synchronized int[][] getBlackGridForCpu() { return blackGrid; }
    public synchronized int[][] getRedGridForCpu()   { return redGrid; }
    public synchronized int[][] getBlackShotsForCpu(){ return blackShots; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getStatus()        { return status; }
    public String getCurrentPlayer() { return currentPlayer; }
    public String getPlacingTurn()   { return placingTurn; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<int[]> shipCells(int row, int col, int size, boolean horizontal) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int r = horizontal ? row     : row + i;
            int c = horizontal ? col + i : col;
            cells.add(new int[]{r, c});
        }
        return cells;
    }

    private boolean isShipSunk(int[][] grid, int shipId) {
        for (int[] row : grid) for (int v : row) if (v == shipId) return false;
        return true;
    }

    private void revealSunkInShots(int[][] target, int[][] shots, int shipId) {
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                if (target[r][c] == -shipId) shots[r][c] = 3;
    }

    private boolean allSunk(int[][] grid) {
        for (int[] row : grid) for (int v : row) if (v > 0) return false;
        return true;
    }

    private int[][] copy2d(int[][] src) {
        int[][] dst = new int[GRID][GRID];
        for (int i = 0; i < GRID; i++) dst[i] = src[i].clone();
        return dst;
    }

    // ── Inner snapshot DTO ────────────────────────────────────────────────────

    public static class BattleshipSnapshot {
        public int[][]  myGrid;
        public int[][]  enemyGrid;
        public int      myShipsPlaced;
        public String   viewingPlayer;
        public String   status;
        public String   currentPlayer;
        public String   placingTurn;
        public String   lastResult;
        public boolean  redReady;
        public boolean  blackReady;
    }
}

