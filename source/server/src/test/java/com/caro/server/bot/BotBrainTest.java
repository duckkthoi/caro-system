package com.caro.server.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class BotBrainTest {

    private static final int SIZE = 15;

    private final char[][] grid = new char[SIZE][SIZE];
    private final Random random = new Random(42);

    BotBrainTest() {
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
    }

    private void place(char mark, int... xy) {
        for (int i = 0; i < xy.length; i += 2) {
            grid[xy[i + 1]][xy[i]] = mark;
        }
    }

    private List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (char[] row : grid) {
            rows.add(new String(row));
        }
        return rows;
    }

    private int[] hard(char me) {
        return BotBrain.chooseMove(rows(), me, false, BotLevel.HARD, random);
    }

    @Test
    void emptyBoardStartsInTheCentre() {
        assertThat(hard('X')).containsExactly(7, 7);
    }

    @Test
    void completesItsOwnFour() {
        place('X', 3, 7, 4, 7, 5, 7, 6, 7);
        place('O', 2, 7, 10, 10, 11, 11, 12, 12);
        assertThat(hard('X')).containsExactly(7, 7);
    }

    @Test
    void blocksTheOpponentsFour() {
        place('X', 3, 5, 4, 5, 5, 5, 6, 5);
        place('O', 2, 5, 10, 10, 11, 12, 13, 9);
        assertThat(hard('O')).containsExactly(7, 5);
    }

    @Test
    void winsInsteadOfBlocking() {
        place('X', 3, 5, 4, 5, 5, 5, 6, 5);        // X threatens to win on (7,5)
        place('O', 2, 5, 3, 10, 4, 10, 5, 10, 7, 10); // but O wins first on (6,10)
        assertThat(hard('O')).containsExactly(6, 10);
    }

    @Test
    void blocksAnOpenThree() {
        place('X', 5, 5, 6, 5, 7, 5);
        place('O', 10, 10);
        assertThat(Arrays.toString(hard('O'))).isIn("[4, 5]", "[8, 5]");
    }

    @Test
    void aFiveBlockedOnBothEndsIsNotAWinWithThatRule() {
        place('X', 3, 4, 4, 4, 5, 4, 6, 4);
        place('O', 2, 4, 8, 4);
        assertThat(BotBrain.cellScore(grid, 7, 4, 'X', false)).isGreaterThanOrEqualTo(BotBrain.WIN);
        assertThat(BotBrain.cellScore(grid, 7, 4, 'X', true)).isLessThan(BotBrain.WIN);
    }

    @Test
    void alwaysPlaysAnEmptyCell() {
        Random fill = new Random(7);
        for (int round = 0; round < 200; round++) {
            for (char[] row : grid) {
                for (int x = 0; x < SIZE; x++) {
                    int r = fill.nextInt(10);
                    row[x] = r == 0 ? 'X' : r == 1 ? 'O' : '.';
                }
            }
            for (BotLevel level : BotLevel.values()) {
                int[] move = BotBrain.chooseMove(rows(), 'O', round % 2 == 0, level, random);
                assertThat(grid[move[1]][move[0]]).as("round %d, %s", round, level).isEqualTo('.');
            }
        }
    }
}
