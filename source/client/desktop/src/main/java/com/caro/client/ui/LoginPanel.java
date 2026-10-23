package com.caro.client.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.model.UserInfo;
import com.caro.client.net.ServerException;

/** Server address, login and registration. Remembers the last server and username. */
final class LoginPanel extends JPanel implements ClientListener {

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginPanel.class);

    private final GameClient client;
    private final JTextField host = new JTextField(14);
    private final JTextField port = new JTextField(5);
    private final JTextField username = new JTextField(16);
    private final JPasswordField password = new JPasswordField(16);
    private final JTextField displayName = new JTextField(16);
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton registerButton = new JButton("Đăng ký tài khoản mới");
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);

    LoginPanel(GameClient client, String defaultHost, int defaultPort) {
        super(new GridBagLayout());
        this.client = client;
        client.addListener(this);

        host.setText(PREFS.get("host", defaultHost));
        port.setText(PREFS.get("port", String.valueOf(defaultPort)));
        username.setText(PREFS.get("username", ""));
        displayName.putClientProperty("JTextField.placeholderText", "Không bắt buộc");

        loginButton.addActionListener(e -> submit(false));
        registerButton.addActionListener(e -> submit(true));
        password.addActionListener(e -> submit(false));

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(getBackground().darker()), Ui.padding(24)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Caro Online", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        add(card, c, 0, 0, 2, title);
        add(card, c, 0, 1, 2, Ui.centered("Cờ ca rô 15×15 · chơi qua mạng TCP"));
        add(card, c, 0, 2, 2, new JSeparator());

        JPanel server = new JPanel(new GridBagLayout());
        GridBagConstraints sc = new GridBagConstraints();
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.weightx = 1;
        server.add(host, sc);
        sc.weightx = 0;
        sc.insets = new Insets(0, 6, 0, 0);
        server.add(port, sc);

        row(card, c, 3, "Server", server);
        row(card, c, 4, "Tên đăng nhập", username);
        row(card, c, 5, "Mật khẩu", password);
        row(card, c, 6, "Tên hiển thị", displayName);
        add(card, c, 0, 7, 2, Ui.muted("Tên hiển thị chỉ dùng khi đăng ký."));

        c.insets = new Insets(14, 4, 4, 4);
        add(card, c, 0, 8, 2, loginButton);
        c.insets = new Insets(4, 4, 4, 4);
        add(card, c, 0, 9, 2, registerButton);
        add(card, c, 0, 10, 2, status);

        add(card);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        getRootPane().setDefaultButton(loginButton);
    }

    void focusFirstEmpty() {
        (username.getText().isBlank() ? username : password).requestFocusInWindow();
    }

    /** Shown when the user is sent back here (kicked, connection lost...). */
    void showMessage(String message) {
        showStatus(message, true);
    }

    private void submit(boolean register) {
        String h = host.getText().trim();
        int p;
        try {
            p = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            showStatus("Cổng phải là một số", true);
            return;
        }
        String user = username.getText().trim();
        String pass = new String(password.getPassword());
        if (h.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showStatus("Nhập đủ server, tên đăng nhập và mật khẩu", true);
            return;
        }

        setBusy(true);
        showStatus(register ? "Đang đăng ký..." : "Đang đăng nhập...", false);
        CompletableFuture<UserInfo> action = register
                ? client.register(h, p, user, pass, displayName.getText())
                : client.login(h, p, user, pass);
        action.whenComplete((me, error) -> {
            setBusy(false);
            if (error != null) {
                showStatus(ServerException.messageOf(error), true);
                return;
            }
            PREFS.put("host", h);
            PREFS.put("port", String.valueOf(p));
            PREFS.put("username", user);
            password.setText("");
            showStatus(" ", false);
        });
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
    }

    private void showStatus(String text, boolean error) {
        status.setForeground(error ? Ui.DANGER : Ui.MUTED);
        status.setText(Ui.html(text));
    }

    private static void row(JPanel panel, GridBagConstraints c, int y, String label, JComponent field) {
        c.weightx = 0;
        add(panel, c, 0, y, 1, new JLabel(label));
        c.weightx = 1;
        add(panel, c, 1, y, 1, field);
        c.weightx = 0;
    }

    private static void add(JPanel panel, GridBagConstraints c, int x, int y, int width, JComponent component) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        panel.add(component, c);
    }
}
