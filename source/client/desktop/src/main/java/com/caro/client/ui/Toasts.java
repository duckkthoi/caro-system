package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.caro.client.app.ClientListener.NoticeKind;

/**
 * Short notices in the bottom-right corner of the window, coloured by kind. Each one disappears after a few
 * seconds or when clicked. They live in the frame's layered pane, above the screens, so they never move the layout.
 * Only used on the Swing thread.
 */
final class Toasts {

    private static final int WIDTH = 340;
    private static final int MARGIN = 16;
    private static final int GAP = 8;
    private static final int MAX_SHOWN = 4;
    private static final int SHOW_MS = 6000;

    private final JFrame frame;
    private final List<Toast> shown = new ArrayList<>(); // oldest first

    Toasts(JFrame frame) {
        this.frame = frame;
        frame.getLayeredPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout();
            }
        });
    }

    void show(String text, NoticeKind kind) {
        if (shown.size() >= MAX_SHOWN) {
            dismiss(shown.get(0));
        }
        Toast toast = new Toast(text, kind);
        shown.add(toast);
        frame.getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
        relayout();
        Timer timer = new Timer(SHOW_MS, e -> dismiss(toast));
        timer.setRepeats(false);
        timer.start();
    }

    private void dismiss(Toast toast) {
        if (shown.remove(toast)) {
            JLayeredPane pane = frame.getLayeredPane();
            pane.remove(toast);
            relayout();
            pane.repaint();
        }
    }

    /** Newest at the bottom, older ones stacked above it. */
    private void relayout() {
        JLayeredPane pane = frame.getLayeredPane();
        int bottom = pane.getHeight() - MARGIN - 28; // keep the status bar visible
        int width = Math.min(WIDTH, Math.max(160, pane.getWidth() - 2 * MARGIN));
        for (int i = shown.size() - 1; i >= 0; i--) {
            Toast toast = shown.get(i);
            toast.setTextWidth(width);
            Dimension size = toast.getPreferredSize();
            bottom -= size.height;
            toast.setBounds(pane.getWidth() - MARGIN - width, bottom, width, size.height);
            bottom -= GAP;
        }
        pane.revalidate();
        pane.repaint();
    }

    static Color color(NoticeKind kind) {
        return switch (kind) {
            case SUCCESS -> Ui.SUCCESS;
            case WARNING -> Ui.WARNING;
            case ERROR -> Ui.DANGER;
            case INFO -> Ui.INFO;
        };
    }

    private static BadgeIcon.Symbol symbol(NoticeKind kind) {
        return switch (kind) {
            case SUCCESS -> BadgeIcon.Symbol.CHECK;
            case WARNING -> BadgeIcon.Symbol.EXCLAIM;
            case ERROR -> BadgeIcon.Symbol.CROSS;
            case INFO -> BadgeIcon.Symbol.INFO;
        };
    }

    /** A card with a coloured strip on the left, an icon and the text. */
    private final class Toast extends JPanel {

        private static final int ARC = 12;
        private static final int STRIP = 5;
        private final Color accent;
        private final String text;
        private final JLabel label = new JLabel();

        Toast(String text, NoticeKind kind) {
            super(new BorderLayout(10, 0));
            this.accent = color(kind);
            this.text = text;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 10 + STRIP, 10, 12));
            JLabel icon = new JLabel(new BadgeIcon(symbol(kind), accent, 22));
            icon.setVerticalAlignment(JLabel.TOP);
            add(icon, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            setToolTipText("Bấm để đóng");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dismiss(Toast.this);
                }
            });
        }

        void setTextWidth(int toastWidth) {
            label.setText(Ui.html(text, toastWidth - 10 - STRIP - 12 - 22 - 10 - 4));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g.setColor(new Color(0, 0, 0, 28)); // soft shadow
                g.fillRoundRect(1, 2, w - 2, h - 2, ARC, ARC);
                Color background = UIManager.getColor("Panel.background");
                g.setColor(background == null ? Color.WHITE : background.brighter());
                g.fillRoundRect(0, 0, w - 1, h - 3, ARC, ARC);
                g.setColor(new Color(0, 0, 0, 40));
                g.drawRoundRect(0, 0, w - 1, h - 3, ARC, ARC);
                g.setClip(0, 0, STRIP, h - 2);
                g.setColor(accent);
                g.fillRoundRect(0, 0, STRIP * 3, h - 2, ARC, ARC);
            } finally {
                g.dispose();
            }
        }
    }
}
