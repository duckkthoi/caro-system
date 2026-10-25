package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.model.OnlineUser;
import com.caro.client.model.RoomInfo;
import com.caro.client.model.UserInfo;

/**
 * The window. Shows one of three screens depending on the client state:
 * not logged in → login, logged in without a room → lobby, in a room → room (board).
 */
public final class MainFrame extends JFrame implements ClientListener {

    private static final String LOGIN = "login";
    private static final String LOBBY = "lobby";
    private static final String ROOM = "room";

    private final GameClient client;
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private final LoginPanel loginPanel;
    private final Invites invites;
    private final JLabel connectionLabel = Ui.muted("Chưa kết nối");
    private final JLabel noticeLabel = new JLabel(" ");
    private final Timer noticeTimer = new Timer(8000, e -> noticeLabel.setText(" "));

    public MainFrame(GameClient client, String defaultHost, int defaultPort) {
        super("Caro Online");
        this.client = client;
        client.addListener(this);
        this.loginPanel = new LoginPanel(client, defaultHost, defaultPort);
        this.invites = new Invites(client, this);

        screens.add(loginPanel, LOGIN);
        screens.add(new LobbyPanel(client, this), LOBBY);
        screens.add(new RoomPanel(client, this), ROOM);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, getBackground().darker()),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        statusBar.add(noticeLabel, BorderLayout.CENTER);
        statusBar.add(connectionLabel, BorderLayout.EAST);
        noticeTimer.setRepeats(false);

        getContentPane().add(screens, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(900, 640));
        setSize(1100, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                client.shutdown();
                System.exit(0);
            }
        });
        cards.show(screens, LOGIN);
        loginPanel.focusFirstEmpty();
    }

    // ------------------------------------------------------------------ navigation used by the screens

    void showProfile(long userId) {
        Ui.run(this, null, client.profile(userId), profile -> new ProfileDialog(this, profile).setVisible(true));
    }

    void showHistory(long userId, String title) {
        new HistoryDialog(this, client, userId, title).setVisible(true);
    }

    void showLeaderboard() {
        new LeaderboardDialog(this, client).setVisible(true);
    }

    void invite(OnlineUser user) {
        invites.invite(user);
    }

    void pickAndInvite() {
        invites.pickAndInvite();
    }

    // ------------------------------------------------------------------ ClientListener

    @Override
    public void connectionChanged(ConnectionStatus status, String message) {
        connectionLabel.setText(switch (status) {
            case CONNECTED -> message;
            case RECONNECTING, CONNECTING -> message;
            case DISCONNECTED -> "Chưa kết nối";
        });
        connectionLabel.setForeground(switch (status) {
            case CONNECTED -> Ui.SUCCESS;
            case RECONNECTING -> Ui.DANGER;
            default -> Ui.MUTED;
        });
    }

    @Override
    public void loggedIn(UserInfo me) {
        setTitle("Caro Online · " + me.displayName() + " (@" + me.username() + ")");
        cards.show(screens, client.room() == null ? LOBBY : ROOM);
    }

    @Override
    public void loggedOut(String reason) {
        setTitle("Caro Online");
        cards.show(screens, LOGIN);
        loginPanel.focusFirstEmpty();
        if (reason != null) {
            loginPanel.showMessage(reason);
            JOptionPane.showMessageDialog(this, reason, "Đã đăng xuất", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void roomChanged(RoomInfo room) {
        if (client.me() != null) {
            cards.show(screens, room == null ? LOBBY : ROOM);
        }
    }

    /** Wraps inside the status bar instead of being cut with "..." when the window is narrow. */
    @Override
    public void notice(String text) {
        noticeLabel.setText(Ui.html(text, Math.max(200, getWidth() - 320)));
        noticeTimer.restart();
    }
}
