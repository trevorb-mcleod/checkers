package org.example.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Minimax CPU player for Connect Four (depth 7, alpha-beta pruning).
 * CPU always plays as BLACK.
 */
@Service
public class Connect4CpuPlayer {

    private static final int DEPTH = 7;
    private final Random rng = new Random();

    public void makeMove(Connect4GameService game) {
        if (!"PLAYING".equals(game.getStatus())) return;
        if (!"BLACK".equals(game.getCurrentPlayer())) return;

        List<Integer> cols = game.getValidColumns();
        if (cols.isEmpty()) return;

        // Shuffle so equal-scored moves vary each game
        List<Integer> shuffled = new ArrayList<>(cols);
        Collections.shuffle(shuffled, rng);

        int bestCol = shuffled.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (int col : shuffled) {
            Connect4GameService copy = game.copy();
            copy.dropPiece("BLACK", col);
            int score = minimax(copy, DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            if (score > bestScore) { bestScore = score; bestCol = col; }
        }

        game.dropPiece("BLACK", bestCol);
    }

    // ── Minimax ───────────────────────────────────────────────────────────────

    private int minimax(Connect4GameService game, int depth, int alpha, int beta, boolean maximizing) {
        String status = game.getStatus();
        if ("BLACK_WINS".equals(status)) return 100_000 + depth;   // win sooner = higher score
        if ("RED_WINS"  .equals(status)) return -100_000 - depth;
        if ("DRAW"      .equals(status)) return 0;
        if (depth == 0)                  return scoreBoard(game.getBoardDirect());

        List<Integer> cols = game.getValidColumns();
        if (cols.isEmpty()) return 0;

        // Centre-first ordering improves pruning
        cols.sort(Comparator.comparingInt(c -> Math.abs(c - Connect4GameService.COLS / 2)));

        String player = game.getCurrentPlayer();

        if (maximizing) {   // BLACK
            int best = Integer.MIN_VALUE;
            for (int col : cols) {
                Connect4GameService copy = game.copy();
                copy.dropPiece(player, col);
                int score = minimax(copy, depth - 1, alpha, beta,
                        "BLACK".equals(copy.getCurrentPlayer()));
                best  = Math.max(best, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            }
            return best;
        } else {            // RED
            int best = Integer.MAX_VALUE;
            for (int col : cols) {
                Connect4GameService copy = game.copy();
                copy.dropPiece(player, col);
                int score = minimax(copy, depth - 1, alpha, beta,
                        "BLACK".equals(copy.getCurrentPlayer()));
                best = Math.min(best, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    // ── Board evaluation ─────────────────────────────────────────────────────

    private int scoreBoard(int[][] board) {
        int score = 0;
        int R = Connect4GameService.ROWS;
        int C = Connect4GameService.COLS;

        // Centre column preference
        for (int r = 0; r < R; r++) {
            if (board[r][C/2] == 2) score += 3;
            if (board[r][C/2] == 1) score -= 3;
        }

        // All horizontal windows
        for (int r = 0; r < R; r++)
            for (int c = 0; c <= C-4; c++)
                score += scoreWindow(board[r][c], board[r][c+1], board[r][c+2], board[r][c+3]);

        // All vertical windows
        for (int c = 0; c < C; c++)
            for (int r = 0; r <= R-4; r++)
                score += scoreWindow(board[r][c], board[r+1][c], board[r+2][c], board[r+3][c]);

        // Diagonal ↘
        for (int r = 0; r <= R-4; r++)
            for (int c = 0; c <= C-4; c++)
                score += scoreWindow(board[r][c], board[r+1][c+1], board[r+2][c+2], board[r+3][c+3]);

        // Diagonal ↗
        for (int r = 3; r < R; r++)
            for (int c = 0; c <= C-4; c++)
                score += scoreWindow(board[r][c], board[r-1][c+1], board[r-2][c+2], board[r-3][c+3]);

        return score;
    }

    private int scoreWindow(int a, int b, int c, int d) {
        int blacks = 0, reds = 0, empties = 0;
        for (int v : new int[]{a, b, c, d}) {
            if      (v == 2) blacks++;
            else if (v == 1) reds++;
            else             empties++;
        }
        if (blacks == 4) return 1000;
        if (blacks == 3 && empties == 1) return 50;
        if (blacks == 2 && empties == 2) return 5;
        if (reds   == 3 && empties == 1) return -50;
        if (reds   == 2 && empties == 2) return -5;
        return 0;
    }
}

