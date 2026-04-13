package org.example.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * CPU opponent for Battleship.
 * Placement: random.
 * Shooting:  hunt/target — checkerboard sweep in hunt mode,
 *            attack adjacent cells after a hit.
 */
@Service
public class BattleshipCpuPlayer {

    private final Random rng = new Random();

    // ── Placement ─────────────────────────────────────────────────────────────

    /** Place all 5 ships randomly for BLACK. */
    public void placeShips(BattleshipGameService game) {
        for (int i = 0; i < BattleshipGameService.SHIP_SIZES.length; i++) {
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts++ < 500) {
                int  row  = rng.nextInt(BattleshipGameService.GRID);
                int  col  = rng.nextInt(BattleshipGameService.GRID);
                boolean h = rng.nextBoolean();
                placed = game.placeShipForce("BLACK", row, col, h);
            }
        }
    }

    // ── Shooting ──────────────────────────────────────────────────────────────

    /**
     * Keep shooting until the CPU misses (which switches the turn back to RED),
     * the game ends, or no valid targets remain.
     * A hit in Battleship grants another turn, so we loop.
     */
    public void makeMove(BattleshipGameService game) {
        while ("PLAYING".equals(game.getStatus())
                && "BLACK".equals(game.getCurrentPlayer())) {
            int[] target = chooseTarget(game.getBlackShotsForCpu());
            if (target == null) break;
            game.shoot("BLACK", target[0], target[1]);
        }
    }

    // ── AI logic ──────────────────────────────────────────────────────────────

    private int[] chooseTarget(int[][] shots) {
        int G = BattleshipGameService.GRID;

        // Collect hit (but not sunk) cells
        List<int[]> hits = new ArrayList<>();
        for (int r = 0; r < G; r++)
            for (int c = 0; c < G; c++)
                if (shots[r][c] == 2) hits.add(new int[]{r, c});

        if (!hits.isEmpty()) {
            // Target mode — if 2+ hits are in a line, continue that direction
            List<int[]> candidates = new ArrayList<>();

            if (hits.size() >= 2) {
                // Sort by row then col to find a consistent line
                hits.sort(Comparator.comparingInt((int[] a) -> a[0]).thenComparingInt(a -> a[1]));
                int[] first = hits.get(0);
                int[] last  = hits.get(hits.size() - 1);
                boolean sameRow = first[0] == last[0];

                int[][] ends = sameRow
                    ? new int[][]{{first[0], first[1] - 1}, {first[0], last[1] + 1}}
                    : new int[][]{{first[0] - 1, first[1]}, {last[0] + 1, first[1]}};

                for (int[] e : ends)
                    if (valid(shots, e[0], e[1])) candidates.add(e);
            }

            // Fallback: shoot adjacent to any hit cell
            if (candidates.isEmpty()) {
                for (int[] h : hits) {
                    int[][] adj = {{h[0]-1,h[1]},{h[0]+1,h[1]},{h[0],h[1]-1},{h[0],h[1]+1}};
                    for (int[] a : adj) if (valid(shots, a[0], a[1])) candidates.add(a);
                }
            }

            if (!candidates.isEmpty()) {
                Collections.shuffle(candidates, rng);
                return candidates.get(0);
            }
        }

        // Hunt mode — checkerboard pattern (every other diagonal skips)
        List<int[]> unknowns = new ArrayList<>();
        for (int r = 0; r < G; r++)
            for (int c = 0; c < G; c++)
                if (shots[r][c] == 0 && (r + c) % 2 == 0) unknowns.add(new int[]{r, c});

        if (unknowns.isEmpty())
            for (int r = 0; r < G; r++)
                for (int c = 0; c < G; c++)
                    if (shots[r][c] == 0) unknowns.add(new int[]{r, c});

        if (unknowns.isEmpty()) return null;
        Collections.shuffle(unknowns, rng);
        return unknowns.get(0);
    }

    private boolean valid(int[][] shots, int r, int c) {
        int G = BattleshipGameService.GRID;
        return r >= 0 && r < G && c >= 0 && c < G && shots[r][c] == 0;
    }
}

