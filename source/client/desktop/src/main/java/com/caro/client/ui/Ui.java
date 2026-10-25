package com.caro.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;

import com.caro.client.net.ServerException;

/** Small helpers shared by the screens. */
final class Ui {

    static final Color X_COLOR = new Color(0xC6, 0x28, 0x28);
    static final Color O_COLOR = new Color(0x15, 0x65, 0xC0);
    static final Color MUTED = new Color(0x66, 0x66, 0x66);
    static final Color SUCCESS = new Color(0x2E, 0x7D, 0x32);
    static final Color DANGER = new Color(0xC6, 0x28, 0x28);

    private Ui() {
    }

    static Color markColor(String mark) {
        return "X".equals(mark) ? X_COLOR : O_COLOR;
    }

    /**
     * Runs an action of the client: the button is disabled until the server answers, errors are shown in a
     * dialog. The futures of GameClient complete on the Swing thread, so {@code onSuccess} may touch the UI.
     */
    static <T> void run(Component parent, AbstractButton button, CompletableFuture<T> action, Consumer<T> onSuccess) {
        if (button != null) {
            button.setEnabled(false);
        }
        action.whenComplete((value, error) -> {
            if (button != null) {
                button.setEnabled(true);
            }
            if (error != null) {
                showError(parent, error);
            } else if (onSuccess != null) {
                onSuccess.accept(value);
            }
        });
    }

    static void showError(Component parent, Throwable error) {
        JOptionPane.showMessageDialog(parent, ServerException.messageOf(error), "Không thực hiện được",
                JOptionPane.WARNING_MESSAGE);
    }

    static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Xác nhận", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 4f));
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    static JLabel centered(String text) {
        return new JLabel(text, SwingConstants.CENTER);
    }

    static Border padding(int size) {
        return BorderFactory.createEmptyBorder(size, size, size, size);
    }

    static <C extends JComponent> C padded(C component, int size) {
        component.setBorder(padding(size));
        return component;
    }

    static String html(String text) {
        return "<html>" + escape(text) + "</html>";
    }

    /**
     * Like {@link #html(String)}, but long lines wrap inside {@code widthPx} instead of being cut with "...".
     * The fixed width lets Swing compute the right height for the wrapped text.
     */
    static String html(String text, int widthPx) {
        return "<html><div style='width:" + cssWidth(widthPx) + "'>" + escape(text) + "</div></html>";
    }

    /** Same, centred. */
    static String htmlCentered(String text, int widthPx) {
        return "<html><div style='width:" + cssWidth(widthPx) + ";text-align:center'>" + escape(text) + "</div></html>";
    }

    /**
     * Swing's HTML renderer (not in W3C mode) draws 1 CSS px as 1.3 screen pixels but 1 pt as exactly one pixel,
     * so a width in pixels has to be written in pt.
     */
    private static String cssWidth(int widthPx) {
        return widthPx + "pt";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
    }

    /**
     * Table cells wrap their text onto several lines and each row grows to fit, instead of cutting the text
     * with "...". Row heights are recomputed when the data or the column widths change.
     */
    static void wrapCells(JTable table) {
        table.setDefaultRenderer(Object.class, new WrappingCellRenderer());
        Runnable fit = () -> SwingUtilities.invokeLater(() -> fitRowHeights(table));
        table.getModel().addTableModelListener(e -> fit.run());
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fit.run();
            }
        });
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent e) {
                fit.run(); // a column was resized
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });
    }

    private static void fitRowHeights(JTable table) {
        int minimum = table.getFontMetrics(table.getFont()).getHeight() + 10;
        for (int row = 0; row < table.getRowCount(); row++) {
            int height = minimum;
            for (int col = 0; col < table.getColumnCount(); col++) {
                Component cell = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                cell.setSize(table.getColumnModel().getColumn(col).getWidth(), Short.MAX_VALUE);
                height = Math.max(height, cell.getPreferredSize().height);
            }
            if (table.getRowHeight(row) != height) {
                table.setRowHeight(row, height);
            }
        }
    }

    /** A cell that wraps at word boundaries (plain text, never HTML). */
    private static final class WrappingCellRenderer extends JTextArea implements TableCellRenderer {

        WrappingCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }
}
