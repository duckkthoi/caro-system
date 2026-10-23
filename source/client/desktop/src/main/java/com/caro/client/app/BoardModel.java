package com.caro.client.app;

import java.util.List;

/** What the board panel draws: the live match, or a position of a replay. */
public interface BoardModel {

    int size();

    /** '.', 'X' or 'O'. */
    char cell(int x, int y);

    /** {x, y} of the last move, or null. */
    int[] lastMove();

    /** Cells of the winning five, empty if none. */
    List<int[]> winLine();
}
