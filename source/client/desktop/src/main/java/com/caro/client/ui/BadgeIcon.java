package com.caro.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/**
 * A round badge with a symbol drawn in vector graphics, so it looks the same on every machine
 * (the UI font does not always have ✓, ⚠ or emoji glyphs).
 */
final class BadgeIcon implements Icon {

    enum Symbol { CHECK, CROSS, EXCLAIM, INFO, STAR, EQUALS }

    private final Symbol symbol;
    private final Color color;
    private final int size;

    BadgeIcon(Symbol symbol, Color color, int size) {
        this.symbol = symbol;
        this.color = color;
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            double s = size;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g.fill(new Ellipse2D.Double(0, 0, s, s));
            g.setColor(color);
            g.setStroke(new BasicStroke((float) Math.max(2, s / 9), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (symbol) {
                case CHECK -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(s * 0.29, s * 0.52);
                    p.lineTo(s * 0.44, s * 0.67);
                    p.lineTo(s * 0.72, s * 0.37);
                    g.draw(p);
                }
                case CROSS -> {
                    g.draw(new Line2D.Double(s * 0.34, s * 0.34, s * 0.66, s * 0.66));
                    g.draw(new Line2D.Double(s * 0.66, s * 0.34, s * 0.34, s * 0.66));
                }
                case EXCLAIM -> {
                    g.draw(new Line2D.Double(s * 0.5, s * 0.27, s * 0.5, s * 0.56));
                    g.draw(new Line2D.Double(s * 0.5, s * 0.72, s * 0.5, s * 0.72));
                }
                case INFO -> {
                    g.draw(new Line2D.Double(s * 0.5, s * 0.28, s * 0.5, s * 0.28));
                    g.draw(new Line2D.Double(s * 0.5, s * 0.44, s * 0.5, s * 0.72));
                }
                case EQUALS -> {
                    g.draw(new Line2D.Double(s * 0.32, s * 0.41, s * 0.68, s * 0.41));
                    g.draw(new Line2D.Double(s * 0.32, s * 0.59, s * 0.68, s * 0.59));
                }
                case STAR -> g.fill(star(s / 2, s / 2, s * 0.32, s * 0.14));
                default -> { }
            }
        } finally {
            g.dispose();
        }
    }

    private static Path2D star(double cx, double cy, double outer, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double px = cx + r * Math.cos(a);
            double py = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        return p;
    }
}
