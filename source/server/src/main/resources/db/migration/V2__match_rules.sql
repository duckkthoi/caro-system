-- Optional "block two ends" rule (docs/rules.md, section 9), stored so a replay recomputes the same winning line.
ALTER TABLE matches ADD COLUMN block_two_ends BOOLEAN NOT NULL DEFAULT FALSE;
