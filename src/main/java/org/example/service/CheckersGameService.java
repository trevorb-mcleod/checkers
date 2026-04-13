package org.example.service;

import org.example.model.GameState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CheckersGameService {

    // Board piece constants — public so AI and helpers can reference them
    public static final int NONE       = 0;
    public static final int RED        = 1;
    public static final int RED_KING   = 2;
    public static final int BLACK      = 3;
    public static final int BLACK_KING = 4;

    private int[][] board;
    private String currentPlayer;
    private String status;
    private int selectedRow;
    private int selectedCol;
    private int[] multiJumpPiece;
    private boolean forcedJumps = true;

    public CheckersGameService() {
        newGame();
    }

    /** Private constructor used by copy() — skips newGame(). */
    private CheckersGameService(boolean init) {
        if (init) newGame();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public synchronized void setForcedJumps(boolean enabled) {
        this.forcedJumps = enabled;
        selectedRow = -1;
        selectedCol = -1;
    }

    public synchronized void newGame() {
        board = new int[8][8];
        currentPlayer = "RED";
        status = "PLAYING";
        selectedRow = -1;
        selectedCol = -1;
        multiJumpPiece = null;
        initBoard();
    }

    public synchronized GameState getState() {
        GameState state = new GameState();

        int[][] boardCopy = new int[8][8];
        for (int i = 0; i < 8; i++) boardCopy[i] = board[i].clone();
        state.setBoard(boardCopy);

        state.setCurrentPlayer(currentPlayer);
        state.setStatus(status);
        state.setSelectedRow(selectedRow);
        state.setSelectedCol(selectedCol);
        state.setMultiJumpPiece(multiJumpPiece);
        state.setForcedJumps(forcedJumps);
        state.setRedCount(countPieces(true));
        state.setBlackCount(countPieces(false));

        if (selectedRow >= 0 && selectedCol >= 0) {
            state.setValidMoves(computeValidMoves(selectedRow, selectedCol));
        } else {
            state.setValidMoves(new ArrayList<>());
        }

        return state;
    }

    public synchronized GameState click(int row, int col) {
        if (!status.equals("PLAYING")) return getState();

        if (multiJumpPiece != null) {
            selectedRow = multiJumpPiece[0];
            selectedCol = multiJumpPiece[1];
            if (row == multiJumpPiece[0] && col == multiJumpPiece[1]) return getState();
            return executeMove(multiJumpPiece[0], multiJumpPiece[1], row, col);
        }

        int piece = board[row][col];

        if (isOwnedBy(piece, currentPlayer)) {
            List<int[]> moves = computeValidMoves(row, col);
            if (!moves.isEmpty()) {
                selectedRow = row;
                selectedCol = col;
            }
            return getState();
        }

        if (piece == NONE && selectedRow >= 0 && selectedCol >= 0) {
            return executeMove(selectedRow, selectedCol, row, col);
        }

        selectedRow = -1;
        selectedCol = -1;
        return getState();
    }

    // -------------------------------------------------------------------------
    // AI helpers
    // -------------------------------------------------------------------------

    /** Deep-copy this game for minimax exploration. */
    public synchronized CheckersGameService copy() {
        CheckersGameService c = new CheckersGameService(false);
        c.board = new int[8][8];
        for (int i = 0; i < 8; i++) c.board[i] = board[i].clone();
        c.currentPlayer   = currentPlayer;
        c.status          = status;
        c.selectedRow     = -1;
        c.selectedCol     = -1;
        c.multiJumpPiece  = multiJumpPiece != null ? multiJumpPiece.clone() : null;
        c.forcedJumps     = forcedJumps;
        return c;
    }

    /** All valid (from, to) pairs for the current player (respects forced-jumps & multi-jump). */
    public synchronized List<int[][]> getAllValidMoves() {
        List<int[][]> result = new ArrayList<>();
        if (!"PLAYING".equals(status)) return result;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isOwnedBy(board[r][c], currentPlayer)) {
                    for (int[] dest : computeValidMoves(r, c)) {
                        result.add(new int[][]{{r, c}, dest});
                    }
                }
            }
        }
        return result;
    }

    /** Execute a move directly — no click-UI logic, no validation. Used by AI. */
    public synchronized void executeMoveDirect(int fromRow, int fromCol, int toRow, int toCol) {
        doMove(fromRow, fromCol, toRow, toCol);
    }

    public synchronized int[][] getBoardDirect() {
        int[][] copy = new int[8][8];
        for (int i = 0; i < 8; i++) copy[i] = board[i].clone();
        return copy;
    }

    public String getCurrentPlayer()         { return currentPlayer; }
    public String getStatus()                { return status; }
    public int[]  getMultiJumpPieceDirect()  { return multiJumpPiece; }

    // -------------------------------------------------------------------------
    // Board initialisation
    // -------------------------------------------------------------------------

    private void initBoard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if ((row + col) % 2 == 1) {
                    if (row < 3)      board[row][col] = BLACK;
                    else if (row > 4) board[row][col] = RED;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Move execution
    // -------------------------------------------------------------------------

    private GameState executeMove(int fromRow, int fromCol, int toRow, int toCol) {
        List<int[]> valid = computeValidMoves(fromRow, fromCol);
        boolean isValid = valid.stream().anyMatch(m -> m[0] == toRow && m[1] == toCol);
        if (!isValid) return getState();
        doMove(fromRow, fromCol, toRow, toCol);
        return getState();
    }

    /** Core board-mutation logic shared by executeMove and executeMoveDirect. */
    private void doMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        board[fromRow][fromCol] = NONE;
        board[toRow][toCol] = piece;

        boolean wasJump = Math.abs(toRow - fromRow) == 2;
        if (wasJump) {
            board[(fromRow + toRow) / 2][(fromCol + toCol) / 2] = NONE;
        }

        boolean justKinged = false;
        if (piece == RED && toRow == 0) {
            board[toRow][toCol] = RED_KING;
            justKinged = true;
        } else if (piece == BLACK && toRow == 7) {
            board[toRow][toCol] = BLACK_KING;
            justKinged = true;
        }

        selectedRow = -1;
        selectedCol = -1;
        multiJumpPiece = null;

        if (wasJump && !justKinged && !jumpsForPiece(toRow, toCol).isEmpty()) {
            multiJumpPiece = new int[]{toRow, toCol};
            selectedRow = toRow;
            selectedCol = toCol;
            return;
        }

        currentPlayer = currentPlayer.equals("RED") ? "BLACK" : "RED";
        checkWinCondition();
    }

    // -------------------------------------------------------------------------
    // Valid move computation
    // -------------------------------------------------------------------------

    private List<int[]> computeValidMoves(int row, int col) {
        if (multiJumpPiece != null) {
            if (multiJumpPiece[0] == row && multiJumpPiece[1] == col) {
                return jumpsForPiece(row, col);
            }
            return new ArrayList<>();
        }

        int piece = board[row][col];
        if (!isOwnedBy(piece, currentPlayer)) return new ArrayList<>();

        if (forcedJumps && playerHasJump(currentPlayer)) {
            return jumpsForPiece(row, col);
        }

        List<int[]> all = new ArrayList<>(simpleMovesForPiece(row, col));
        all.addAll(jumpsForPiece(row, col));
        return all;
    }

    private List<int[]> jumpsForPiece(int row, int col) {
        List<int[]> jumps = new ArrayList<>();
        int piece = board[row][col];
        if (piece == NONE) return jumps;

        for (int rd : rowDirections(piece)) {
            for (int cd : new int[]{-1, 1}) {
                int midR = row + rd,   midC = col + cd;
                int endR = row + 2*rd, endC = col + 2*cd;
                if (inBounds(endR, endC)
                        && isOpponentOf(piece, board[midR][midC])
                        && board[endR][endC] == NONE) {
                    jumps.add(new int[]{endR, endC});
                }
            }
        }
        return jumps;
    }

    private List<int[]> simpleMovesForPiece(int row, int col) {
        List<int[]> moves = new ArrayList<>();
        int piece = board[row][col];
        if (piece == NONE) return moves;

        for (int rd : rowDirections(piece)) {
            for (int cd : new int[]{-1, 1}) {
                int nr = row + rd, nc = col + cd;
                if (inBounds(nr, nc) && board[nr][nc] == NONE) {
                    moves.add(new int[]{nr, nc});
                }
            }
        }
        return moves;
    }

    private boolean playerHasJump(String player) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (isOwnedBy(board[r][c], player) && !jumpsForPiece(r, c).isEmpty())
                    return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Win condition
    // -------------------------------------------------------------------------

    private void checkWinCondition() {
        if (countPieces(true) == 0) {
            status = "BLACK_WINS";
            return;
        }
        if (countPieces(false) == 0) {
            status = "RED_WINS";
            return;
        }
        // Stalemate: current player has no legal moves → they lose
        boolean currentHasMoves = false;
        outer:
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isOwnedBy(board[r][c], currentPlayer)) {
                    if (!jumpsForPiece(r, c).isEmpty() || !simpleMovesForPiece(r, c).isEmpty()) {
                        currentHasMoves = true;
                        break outer;
                    }
                }
            }
        }
        if (!currentHasMoves) {
            status = "RED".equals(currentPlayer) ? "BLACK_WINS" : "RED_WINS";
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int[] rowDirections(int piece) {
        return switch (piece) {
            case RED   -> new int[]{-1};
            case BLACK -> new int[]{1};
            default    -> new int[]{-1, 1};
        };
    }

    private boolean isOwnedBy(int piece, String player) {
        if (player.equals("RED"))   return piece == RED   || piece == RED_KING;
        if (player.equals("BLACK")) return piece == BLACK || piece == BLACK_KING;
        return false;
    }

    private boolean isOpponentOf(int forPiece, int other) {
        if (other == NONE) return false;
        boolean forIsRed   = (forPiece == RED || forPiece == RED_KING);
        boolean otherIsRed = (other    == RED || other    == RED_KING);
        return forIsRed != otherIsRed;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private int countPieces(boolean red) {
        int n = 0;
        for (int[] row : board)
            for (int p : row)
                if (red ? (p == RED || p == RED_KING) : (p == BLACK || p == BLACK_KING)) n++;
        return n;
    }
}

