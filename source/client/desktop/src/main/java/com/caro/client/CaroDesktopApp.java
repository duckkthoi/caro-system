package com.caro.client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.caro.client.app.GameClient;
import com.caro.client.ui.MainFrame;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Desktop client of Caro Online. Talks to the server over raw TCP (docs/protocol.md).
 *
 * <p>Default server: {@code --host=localhost --port=9090}, or the environment variables CARO_HOST / CARO_PORT.
 * The login screen lets the user change it and remembers the last one.
 */
public final class CaroDesktopApp {

    private CaroDesktopApp() {
    }

    public static void main(String[] args) {
        String host = option(args, "host", System.getenv().getOrDefault("CARO_HOST", "localhost"));
        int port = Integer.parseInt(option(args, "port", System.getenv().getOrDefault("CARO_PORT", "9090")));

        FlatLightLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Table.showHorizontalLines", true);

        SwingUtilities.invokeLater(() -> {
            GameClient client = new GameClient(SwingUtilities::invokeLater);
            new MainFrame(client, host, port).setVisible(true);
        });
    }

    private static String option(String[] args, String name, String fallback) {
        String prefix = "--" + name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }
}
