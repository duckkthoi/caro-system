package com.caro.server.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Square caro board. x is the column, y is the row, (0,0) is the top-left corner.
 * Not thread-safe: the owning match guards it with its own lock.
 */
public final class Board {

    private final int size;
    private final Mark[] cells;
    private int filled;

    public Board(int size) {
        if (size < 5) {
            throw new IllegalArgumentException("Board size must be >= 5");
        }
        this.size = size;
        this.cells = new Mark[size * size];
    }

    public int size() {
        return size;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    /** @return the mark at (x,y), or null if the cell is empty */
    public Mark get(int x, int y) {
        return cells[y * size + x];
    }

    public boolean isEmpty(int x, int y) {
        return get(x, y) == null;
    }

    public void place(int x, int y, Mark mark) {
        if (!inBounds(x, y) || !isEmpty(x, y)) {
            throw new IllegalStateException("Cannot place at (" + x + "," + y + ")");
        }
        cells[y * size + x] = mark;
        filled++;
    }

    public boolean isFull() {
        return filled == cells.length;
    }

    /** One string per row, '.' for empty cells. Used in MATCH_STATE messages. */
    public List<String> toRows() {
        List<String> rows = new ArrayList<>(size);
        for (int y = 0; y < size; y++) {
            StringBuilder row = new StringBuilder(size);
            for (int x = 0; x < size; x++) {
                Mark m = get(x, y);
                row.append(m == null ? '.' : m.symbol());
            }
            rows.add(row.toString());
        }
        return rows;
    }
}
