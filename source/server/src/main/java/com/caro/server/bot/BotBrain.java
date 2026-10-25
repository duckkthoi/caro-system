package com.caro.server.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Chooses the bot's move from a board snapshot. Pure function, no state: easy to unit test.
 *
 * <p>A one-move-ahead heuristic. Every empty cell near the existing stones gets a score: how strong a line the bot
 * would make there (attack) plus how strong a line it would take away from the opponent (defense). Along each of
 * the four directions the line is the run of same-colour stones through the cell, valued by its length and by how
 * many of its ends are still open:
 *
 * <pre>
 *   5+ in a row ......... WIN          (unless "block two ends" is on and both ends touch the opponent)
 *   open four  _XXXX_ ... 50 000       (cannot be stopped)
 *   four       OXXXX_ ...  5 000
 *   open three _XXX_ ....  2 000
 *   ...
 * </pre>
 *
 * Defense is weighted a bit lower than attack, so the bot wins when it can instead of blocking, but blocks a
 * five or an open four before building its own threats.
 */
public final class BotBrain {

    static final int WIN = 1_000_000;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int WIN_LENGTH = 5;
    private static final int NEIGHBOURHOOD = 2; // only cells this close to a stone are worth considering
    private static final int EASY_CHOICES = 4;

    private BotBrain() {
    }

    /**
     * @param rows one string per row y, character x is '.', 'X' or 'O' (same as MatchState.board)
     * @param me   the bot's mark, 'X' or 'O'
     * @return {x, y} of an empty cell
     */
    public static int[] chooseMove(List<String> rows, char me, boolean blockTwoEnds, BotLevel level,
                                   RandomGenerator random) {
        char[][] board = toGrid(rows);
        int n = board.length;
        char opponent = me == 'X' ? 'O' : 'X';
        double defenseWeight = level == BotLevel.EASY ? 0.5 : 0.9;

        List<Candidate> candidates = new ArrayList<>();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (board[y][x] == '.' && hasNeighbour(board, x, y)) {
                    double score = cellScore(board, x, y, me, blockTwoEnds)
                            + defenseWeight * cellScore(board, x, y, opponent, blockTwoEnds);
                    // a tiny random part breaks ties, so the bot does not always play the same game
                    candidates.add(new Candidate(x, y, score + random.nextDouble()));
                }
            }
        }
        if (candidates.isEmpty()) {
            return firstMove(board, random);
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        Candidate chosen = level == BotLevel.EASY
                ? candidates.get(random.nextInt(Math.min(EASY_CHOICES, candidates.size())))
                : candidates.get(0);
        return new int[] {chosen.x(), chosen.y()};
    }

    /** How good it is for {@code mark} to have a stone on (x, y): the sum over the four directions. */
    static int cellScore(char[][] board, int x, int y, char mark, boolean blockTwoEnds) {
        int total = 0;
        for (int[] d : DIRECTIONS) {
            int forward = run(board, x, y, d[0], d[1], mark);
            int backward = run(board, x, y, -d[0], -d[1], mark);
            int length = forward + backward + 1;
            char endA = cellOrEdge(board, x + (forward + 1) * d[0], y + (forward + 1) * d[1]);
            char endB = cellOrEdge(board, x - (backward + 1) * d[0], y - (backward + 1) * d[1]);
            total += lineScore(length, endA, endB, mark, blockTwoEnds);
        }
        return total;
    }

    private static int lineScore(int length, char endA, char endB, char mark, boolean blockTwoEnds) {
        if (length >= WIN_LENGTH) {
            char opponent = mark == 'X' ? 'O' : 'X';
            boolean blockedBothEnds = endA == opponent && endB == opponent; // the edge ('#') never blocks
            return blockTwoEnds && blockedBothEnds ? 0 : WIN;
        }
        int open = (endA == '.' ? 1 : 0) + (endB == '.' ? 1 : 0);
        if (open == 0) {
            return 0; // dead: can never become five
        }
        return switch (length) {
            case 4 -> open == 2 ? 50_000 : 5_000;
            case 3 -> open == 2 ? 2_000 : 200;
            case 2 -> open == 2 ? 100 : 20;
            default -> open == 2 ? 5 : 1;
        };
    }

    /** Number of {@code mark} stones right after (x, y) in direction (dx, dy). */
    private static int run(char[][] board, int x, int y, int dx, int dy, char mark) {
        int count = 0;
        for (int cx = x + dx, cy = y + dy; inBounds(board, cx, cy) && board[cy][cx] == mark; cx += dx, cy += dy) {
            count++;
        }
        return count;
    }

    private static boolean hasNeighbour(char[][] board, int x, int y) {
        for (int dy = -NEIGHBOURHOOD; dy <= NEIGHBOURHOOD; dy++) {
            for (int dx = -NEIGHBOURHOOD; dx <= NEIGHBOURHOOD; dx++) {
                if (inBounds(board, x + dx, y + dy) && board[y + dy][x + dx] != '.') {
                    return true;
                }
            }
        }
        return false;
    }

    /** Empty board: the centre. (A full board never reaches here: the match is already over.) */
    private static int[] firstMove(char[][] board, RandomGenerator random) {
        int c = board.length / 2;
        if (board[c][c] == '.') {
            return new int[] {c, c};
        }
        while (true) {
            int x = random.nextInt(board.length);
            int y = random.nextInt(board.length);
            if (board[y][x] == '.') {
                return new int[] {x, y};
            }
        }
    }

    private static char cellOrEdge(char[][] board, int x, int y) {
        return inBounds(board, x, y) ? board[y][x] : '#';
    }

    private static boolean inBounds(char[][] board, int x, int y) {
        return y >= 0 && y < board.length && x >= 0 && x < board.length;
    }

    private static char[][] toGrid(List<String> rows) {
        char[][] grid = new char[rows.size()][];
        for (int y = 0; y < rows.size(); y++) {
            grid[y] = rows.get(y).toCharArray();
        }
        return grid;
    }

    private record Candidate(int x, int y, double score) {
    }
}
