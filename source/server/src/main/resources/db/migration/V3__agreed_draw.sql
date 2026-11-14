-- Draw by agreement (docs/rules.md, section 14) adds the end reason AGREED.
ALTER TABLE matches DROP CONSTRAINT ck_matches_reason;
ALTER TABLE matches ADD CONSTRAINT ck_matches_reason CHECK (end_reason IN
    ('FIVE_IN_ROW', 'BOARD_FULL', 'RESIGN', 'TIMEOUT', 'DISCONNECT', 'BOTH_DISCONNECTED', 'AGREED'));
