package com.caro.server.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Win detection (docs/rules.md, sections 1 and 9): a player wins with winLength or more stones in a row,
 * horizontally, vertically or diagonally. With the optional "block two ends" rule, a line whose both ends touch
 * an opponent stone does not count; the board edge never counts as a block.
 */
public final class RuleEngine {

    /** Horizontal, vertical, main diagonal, anti-diagonal. */
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private RuleEngine() {
    }

    /**
     * Only lines through the last move can have become winning, so only those four lines are checked.
     *
     * @return the cells of the winning line as [x, y] pairs, or an empty list if the move did not win
     */
    public static List<int[]> findWinLine(Board board, int x, int y, int winLength) {
        return findWinLine(board, x, y, winLength, false);
    }

    /** Same as {@link #findWinLine(Board, int, int, int)}, optionally with the "block two ends" rule. */
    public static List<int[]> findWinLine(Board board, int x, int y, int winLength, boolean blockTwoEnds) {
        Mark mark = board.get(x, y);
        if (mark == null) {
            return List.of();
        }
        for (int[] d : DIRECTIONS) {
            List<int[]> line = new ArrayList<>();
            collect(board, x - d[0], y - d[1], -d[0], -d[1], mark, line);
            Collections.reverse(line);
            line.add(new int[] {x, y});
            collect(board, x + d[0], y + d[1], d[0], d[1], mark, line);
            if (line.size() >= winLength && !(blockTwoEnds && blockedAtBothEnds(board, line, d, mark))) {
                return line;
            }
        }
        return List.of();
    }

    /** The line runs along direction d; an end is blocked if the next cell holds the opponent's mark. */
    private static boolean blockedAtBothEnds(Board board, List<int[]> line, int[] d, Mark mark) {
        int[] first = line.get(0);
        int[] last = line.get(line.size() - 1);
        return isOpponent(board, first[0] - d[0], first[1] - d[1], mark)
                && isOpponent(board, last[0] + d[0], last[1] + d[1], mark);
    }

    private static boolean isOpponent(Board board, int x, int y, Mark mark) {
        return board.inBounds(x, y) && board.get(x, y) == mark.opposite();
    }

    private static void collect(Board board, int x, int y, int dx, int dy, Mark mark, List<int[]> out) {
        while (board.inBounds(x, y) && board.get(x, y) == mark) {
            out.add(new int[] {x, y});
            x += dx;
            y += dy;
        }
    }
}
