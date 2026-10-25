package com.caro.server.match;

/**
 * Per-match settings chosen when the room is created.
 *
 * @param blockTwoEnds a five blocked by the opponent on both ends does not win (docs/rules.md, section 9)
 * @param rated        Elo changes and the match is saved; false for matches against the bot (section 12)
 */
public record MatchOptions(boolean blockTwoEnds, boolean rated) {

    public static final MatchOptions STANDARD = new MatchOptions(false, true);
}
