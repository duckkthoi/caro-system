package com.caro.client.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.caro.client.app.MatchView.EndKind;
import com.caro.client.model.MatchResult;
import com.caro.client.model.MatchState;
import com.caro.client.model.PlayerInfo;

/** How a finished match ended for me: the result dialog is chosen from this. */
class MatchViewTest {

    private static final PlayerInfo AN = new PlayerInfo(1, "an", "An", 1000);
    private static final PlayerInfo BINH = new PlayerInfo(2, "binh", "Bình", 1000);

    private static MatchView match(String yourMark) {
        List<String> board = Collections.nCopies(15, ".".repeat(15));
        return MatchView.of(new MatchState("m1", "R1", 15, false, true, AN, BINH, yourMark, "IN_PROGRESS", "X",
                0, board, null, 30_000L, 30_000, null, null));
    }

    private static MatchView ended(String yourMark, String result) {
        MatchView m = match(yourMark);
        Long winner = switch (result) {
            case "X_WIN" -> AN.id();
            case "O_WIN" -> BINH.id();
            default -> null;
        };
        m.finish(new MatchResult("m1", result, "FIVE_IN_ROW", winner, null, Map.of("X", 16, "O", -16)));
        return m;
    }

    @Test
    void noKindBeforeTheEnd() {
        assertNull(match("X").endKind());
    }

    @Test
    void winOrLossDependsOnMyMark() {
        assertEquals(EndKind.WIN, ended("X", "X_WIN").endKind());
        assertEquals(EndKind.LOSS, ended("O", "X_WIN").endKind());
        assertEquals(EndKind.WIN, ended("O", "O_WIN").endKind());
        assertEquals(EndKind.LOSS, ended("X", "O_WIN").endKind());
    }

    @Test
    void drawAbortedAndSpectator() {
        assertEquals(EndKind.DRAW, ended("X", "DRAW").endKind());
        assertEquals(EndKind.ABORTED, ended("O", "ABORTED").endKind());
        assertEquals(EndKind.WATCHED, ended(null, "X_WIN").endKind());
        assertEquals(EndKind.WATCHED, ended(null, "DRAW").endKind());
        assertEquals(EndKind.ABORTED, ended(null, "ABORTED").endKind());
    }
}
