package com.caro.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

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
        return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</html>";
    }
}
