package com.caro.server.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final Board board = new Board(15);

    private void place(Mark mark, int... xy) {
        for (int i = 0; i < xy.length; i += 2) {
            board.place(xy[i], xy[i + 1], mark);
        }
    }

    @Test
    void fourInARowIsNotAWin() {
        place(Mark.X, 3, 3, 4, 3, 5, 3, 6, 3);
        assertThat(RuleEngine.findWinLine(board, 6, 3, 5)).isEmpty();
    }

    @Test
    void horizontalFiveWins() {
        place(Mark.X, 3, 3, 4, 3, 5, 3, 6, 3, 7, 3);
        List<int[]> line = RuleEngine.findWinLine(board, 5, 3, 5);
        assertThat(line).hasSize(5);
        assertThat(line.get(0)).containsExactly(3, 3);
        assertThat(line.get(4)).containsExactly(7, 3);
    }

    @Test
    void verticalFiveWins() {
        place(Mark.O, 0, 10, 0, 11, 0, 12, 0, 13, 0, 14);
        assertThat(RuleEngine.findWinLine(board, 0, 14, 5)).hasSize(5);
    }

    @Test
    void mainDiagonalFiveWins() {
        place(Mark.X, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4);
        assertThat(RuleEngine.findWinLine(board, 2, 2, 5)).hasSize(5);
    }

    @Test
    void antiDiagonalFiveWinsAtTheBorder() {
        place(Mark.X, 14, 0, 13, 1, 12, 2, 11, 3, 10, 4);
        assertThat(RuleEngine.findWinLine(board, 10, 4, 5)).hasSize(5);
    }

    @Test
    void sixInARowAlsoWins() {
        place(Mark.X, 0, 7, 1, 7, 2, 7, 4, 7, 5, 7);
        place(Mark.X, 3, 7); // fills the gap: 6 in a row
        assertThat(RuleEngine.findWinLine(board, 3, 7, 5)).hasSize(6);
    }

    @Test
    void blockedOnBothEndsStillWins() {
        place(Mark.O, 2, 5, 8, 5);
        place(Mark.X, 3, 5, 4, 5, 5, 5, 6, 5, 7, 5);
        assertThat(RuleEngine.findWinLine(board, 7, 5, 5)).hasSize(5);
    }

    @Test
    void opponentStoneBreaksTheLine() {
        place(Mark.X, 0, 0, 1, 0, 3, 0, 4, 0, 5, 0);
        place(Mark.O, 2, 0);
        assertThat(RuleEngine.findWinLine(board, 5, 0, 5)).isEmpty();
    }

    @Test
    void boardReportsFullAndRows() {
        Board small = new Board(5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                small.place(x, y, (x + y) % 2 == 0 ? Mark.X : Mark.O);
            }
        }
        assertThat(small.isFull()).isTrue();
        assertThat(small.toRows()).first().isEqualTo("XOXOX");
    }
}
