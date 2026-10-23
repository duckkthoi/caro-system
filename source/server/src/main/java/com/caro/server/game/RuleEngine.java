package com.caro.server.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Win detection (docs/rules.md, section 1): a player wins with winLength or more stones in a row,
 * horizontally, vertically or diagonally. Blocked ends do not matter.
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
            if (line.size() >= winLength) {
                return line;
            }
        }
        return List.of();
    }

    private static void collect(Board board, int x, int y, int dx, int dy, Mark mark, List<int[]> out) {
        while (board.inBounds(x, y) && board.get(x, y) == mark) {
            out.add(new int[] {x, y});
            x += dx;
            y += dy;
        }
    }
}
