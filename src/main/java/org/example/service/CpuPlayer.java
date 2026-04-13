package org.example.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Minimax-based CPU player (depth-4, alpha-beta pruning).
 * CPU always plays as BLACK.
 */
@Service
public class CpuPlayer {

    private static final int DEPTH = 4;

    // ── Public entry point ───────────────────────────────────────────────────

    public void makeMove(CheckersGameService game) {
        List<int[][]> moves = game.getAllValidMoves();
        if (moves.isEmpty()) return;

        // Shuffle so equal-scored moves vary each game
        Collections.shuffle(moves);

        int bestScore = Integer.MIN_VALUE;
        int[][] bestMove = null;

        for (int[][] move : moves) {
            CheckersGameService copy = game.copy();
            copy.executeMoveDirect(move[0][0], move[0][1], move[1][0], move[1][1]);
            completeMultiJump(copy);

            boolean nextIsBlack = "BLACK".equals(copy.getCurrentPlayer());
            int score = minimax(copy, DEPTH - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, nextIsBlack);
            if (score > bestScore) {
                bestScore = score;
                bestMove  = move;
            }
        }

        if (bestMove != null) {
            game.executeMoveDirect(bestMove[0][0], bestMove[0][1], bestMove[1][0], bestMove[1][1]);
            completeMultiJump(game);  // finish any multi-jump chain
        }
    }

    // ── Minimax ───────────────────────────────────────────────────────────────

    private int minimax(CheckersGameService game, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || !"PLAYING".equals(game.getStatus())) {
            return evaluate(game);
        }

        List<int[][]> moves = game.getAllValidMoves();
        if (moves.isEmpty()) return evaluate(game);

        if (maximizing) {  // BLACK's turn
            int best = Integer.MIN_VALUE;
            for (int[][] move : moves) {
                CheckersGameService copy = game.copy();
                copy.executeMoveDirect(move[0][0], move[0][1], move[1][0], move[1][1]);
                completeMultiJump(copy);
                boolean nextMax = "BLACK".equals(copy.getCurrentPlayer());
                int score = minimax(copy, depth - 1, alpha, beta, nextMax);
                best  = Math.max(best, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            }
            return best;
        } else {           // RED's turn
            int best = Integer.MAX_VALUE;
            for (int[][] move : moves) {
                CheckersGameService copy = game.copy();
                copy.executeMoveDirect(move[0][0], move[0][1], move[1][0], move[1][1]);
                completeMultiJump(copy);
                boolean nextMax = "BLACK".equals(copy.getCurrentPlayer());
                int score = minimax(copy, depth - 1, alpha, beta, nextMax);
                best = Math.min(best, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    // ── Board evaluation ─────────────────────────────────────────────────────

    private int evaluate(CheckersGameService game) {
        if ("BLACK_WINS".equals(game.getStatus())) return  100_000;
        if ("RED_WINS"  .equals(game.getStatus())) return -100_000;

        int[][] board = game.getBoardDirect();
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                switch (board[r][c]) {
                    case CheckersGameService.BLACK      -> score += 100 + r * 4;        // advances toward row 7
                    case CheckersGameService.BLACK_KING -> score += 200 + centerBonus(r, c);
                    case CheckersGameService.RED        -> score -= (100 + (7 - r) * 4); // advances toward row 0
                    case CheckersGameService.RED_KING   -> score -= (200 + centerBonus(r, c));
                }
            }
        }
        return score;
    }

    /** Small bonus for occupying the center of the board (kings benefit most). */
    private int centerBonus(int r, int c) {
        int dr = Math.abs(r - 3) + Math.abs(r - 4);
        int dc = Math.abs(c - 3) + Math.abs(c - 4);
        return Math.max(0, 10 - (dr + dc) * 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** After an initial jump, keep jumping if a multi-jump chain is available. */
    private void completeMultiJump(CheckersGameService game) {
        int guard = 0;
        while (game.getMultiJumpPieceDirect() != null && guard++ < 6) {
            List<int[][]> cont = game.getAllValidMoves();
            if (cont.isEmpty()) break;
            int[][] m = cont.get(0);
            game.executeMoveDirect(m[0][0], m[0][1], m[1][0], m[1][1]);
        }
    }
}

