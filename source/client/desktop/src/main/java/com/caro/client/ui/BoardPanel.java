package com.caro.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

import javax.swing.JComponent;

import com.caro.client.app.BoardModel;

/**
 * Draws a caro board: stones are placed <i>in</i> the cells. Highlights the last move and the winning line,
 * and previews my mark under the mouse when I may play.
 */
final class BoardPanel extends JComponent {

    private static final Color BACKGROUND = new Color(0xF5, 0xE6, 0xC8);
    private static final Color GRID = new Color(0xB8, 0x9B, 0x6A);
    private static final Color LAST_MOVE = new Color(0xFF, 0xF1, 0x76);
    private static final Color WIN = new Color(0xA5, 0xD6, 0xA7);
    private static final Color LABEL = new Color(0x8D, 0x6E, 0x3F);
    private static final int MARGIN = 22; // room for the coordinates

    private BoardModel model;
    private String previewMark;           // shown under the mouse when clickable
    private boolean interactive;
    private BiConsumer<Integer, Integer> onCellClicked = (x, y) -> { };
    private int hoverX = -1;
    private int hoverY = -1;

    BoardPanel() {
        setPreferredSize(new Dimension(620, 620));
        setMinimumSize(new Dimension(300, 300));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int[] cell = cellAt(e.getX(), e.getY());
                int x = cell == null ? -1 : cell[0];
                int y = cell == null ? -1 : cell[1];
                if (x != hoverX || y != hoverY) {
                    hoverX = x;
                    hoverY = y;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                hoverY = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int[] cell = cellAt(e.getX(), e.getY());
                if (interactive && cell != null) {
                    onCellClicked.accept(cell[0], cell[1]);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setModel(BoardModel model) {
        this.model = model;
        repaint();
    }

    /** @param mark my mark to preview under the mouse, or null */
    void setInteractive(boolean interactive, String mark) {
        this.interactive = interactive;
        this.previewMark = mark;
        repaint();
    }

    void onCellClicked(BiConsumer<Integer, Integer> handler) {
        this.onCellClicked = handler;
    }

    // ------------------------------------------------------------------ geometry

    private int boardSize() {
        return model == null ? 15 : model.size();
    }

    private int cellSize() {
        int n = boardSize();
        return Math.max(8, Math.min(getWidth() - 2 * MARGIN, getHeight() - 2 * MARGIN) / n);
    }

    private int originX() {
        return (getWidth() - cellSize() * boardSize()) / 2;
    }

    private int originY() {
        return (getHeight() - cellSize() * boardSize()) / 2;
    }

    private int[] cellAt(int px, int py) {
        int c = cellSize();
        int x = Math.floorDiv(px - originX(), c);
        int y = Math.floorDiv(py - originY(), c);
        int n = boardSize();
        return x >= 0 && y >= 0 && x < n && y < n ? new int[] {x, y} : null;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int n = boardSize();
        int c = cellSize();
        int ox = originX();
        int oy = originY();

        g.setColor(BACKGROUND);
        g.fillRoundRect(ox - 6, oy - 6, c * n + 12, c * n + 12, 10, 10);

        if (model != null) {
            for (int[] cell : model.winLine()) {
                fillCell(g, cell[0], cell[1], WIN);
            }
            int[] last = model.lastMove();
            if (last != null) {
                fillCell(g, last[0], last[1], LAST_MOVE);
            }
        }

        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= n; i++) {
            g.drawLine(ox, oy + i * c, ox + n * c, oy + i * c);
            g.drawLine(ox + i * c, oy, ox + i * c, oy + n * c);
        }
        drawCoordinates(g, n, c, ox, oy);

        if (model != null) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    char mark = model.cell(x, y);
                    if (mark != '.') {
                        drawMark(g, x, y, String.valueOf(mark), 1f);
                    }
                }
            }
            if (interactive && previewMark != null && hoverX >= 0 && model.cell(hoverX, hoverY) == '.') {
                drawMark(g, hoverX, hoverY, previewMark, 0.3f);
            }
        }
        g.dispose();
    }

    private void fillCell(Graphics2D g, int x, int y, Color color) {
        int c = cellSize();
        g.setColor(color);
        g.fillRect(originX() + x * c, originY() + y * c, c, c);
    }

    private void drawMark(Graphics2D g, int x, int y, String mark, float alpha) {
        int c = cellSize();
        int pad = Math.max(3, c / 5);
        int left = originX() + x * c + pad;
        int top = originY() + y * c + pad;
        int size = c - 2 * pad;
        Color base = Ui.markColor(mark);
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(alpha * 255)));
        g.setStroke(new BasicStroke(Math.max(2f, c / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if ("X".equals(mark)) {
            g.drawLine(left, top, left + size, top + size);
            g.drawLine(left + size, top, left, top + size);
        } else {
            g.drawOval(left, top, size, size);
        }
    }

    private void drawCoordinates(Graphics2D g, int n, int c, int ox, int oy) {
        g.setColor(LABEL);
        g.setFont(getFont().deriveFont(Font.PLAIN, Math.max(9f, Math.min(12f, c / 2.6f))));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            String s = String.valueOf(i);
            g.drawString(s, ox + i * c + (c - fm.stringWidth(s)) / 2, oy - 9);
            g.drawString(s, ox - 9 - fm.stringWidth(s), oy + i * c + (c + fm.getAscent()) / 2 - 2);
        }
    }
}
