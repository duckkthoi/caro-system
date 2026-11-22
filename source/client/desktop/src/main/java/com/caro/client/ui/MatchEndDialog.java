package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.app.MatchView;
import com.caro.client.app.MatchView.EndKind;
import com.caro.client.model.MatchResult;
import com.caro.client.model.PlayerInfo;
import com.caro.client.model.RoomInfo;
import com.caro.client.model.UserInfo;

/**
 * The result of the match that just ended, in the middle of the window: who won, why, the Elo change and the
 * next step (look at the board, leave, rematch). Modeless, so the board stays usable behind it.
 * {@link MainFrame} opens it on a live MATCH_END only and closes it when a new match starts or I leave the room.
 */
final class MatchEndDialog extends JDialog implements ClientListener {

    private static final int TEXT_WIDTH = 300;

    private final GameClient client;
    private final MatchView match;
    private final EndKind kind;
    private final JLabel note = Ui.centered(" ");
    private final JLabel elo = Ui.centered(" ");
    private final JButton rematchButton = new JButton("Chơi lại (đổi quân)");
    private final JButton leaveButton;

    MatchEndDialog(JFrame owner, GameClient client, MatchView match) {
        super(owner, "Kết quả trận đấu", ModalityType.MODELESS);
        this.client = client;
        this.match = match;
        this.kind = match.endKind();
        MatchResult result = match.result();
        Color accent = accent(kind);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(5, 0, 0, 0, accent),
                BorderFactory.createEmptyBorder(22, 28, 18, 28)));

        JLabel icon = new JLabel(new BadgeIcon(symbol(kind), accent, 72));
        JLabel title = Ui.centered(Texts.endTitle(kind, result.result()));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 9f));
        title.setForeground(accent);
        String reason = Texts.reason(result.reason()) + (kind == EndKind.ABORTED ? " · Không tính điểm" : "");
        JLabel reasonLabel = Ui.centered(Ui.htmlCentered(reason, TEXT_WIDTH));
        reasonLabel.setFont(reasonLabel.getFont().deriveFont(Font.BOLD));
        JLabel players = Ui.centered(Ui.htmlCentered(playersText(), TEXT_WIDTH));
        players.setForeground(Ui.MUTED);
        elo.setFont(elo.getFont().deriveFont(Font.BOLD, elo.getFont().getSize2D() + 3f));
        note.setForeground(Ui.WARNING);
        note.setFont(note.getFont().deriveFont(Font.BOLD));

        for (JComponent c : new JComponent[] {icon, title, reasonLabel, players, elo, note}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        body.add(icon);
        body.add(Box.createVerticalStrut(12));
        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(reasonLabel);
        body.add(Box.createVerticalStrut(4));
        body.add(players);
        body.add(Box.createVerticalStrut(10));
        body.add(elo);
        body.add(Box.createVerticalStrut(4));
        body.add(note);

        JButton closeButton = Ui.button("Xem bàn cờ", this::dispose);
        leaveButton = new JButton(match.isSpectating() ? "Thôi xem" : "Rời phòng");
        leaveButton.addActionListener(e -> Ui.run(this, leaveButton, client.leaveRoom(), ok -> dispose()));
        rematchButton.addActionListener(e -> Ui.run(this, rematchButton, client.rematch(), ok -> refresh()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 16, 18, 16));
        buttons.add(closeButton);
        buttons.add(leaveButton);
        if (canEverRematch()) {
            buttons.add(rematchButton);
            getRootPane().setDefaultButton(rematchButton);
        } else {
            getRootPane().setDefaultButton(closeButton);
        }

        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        client.addListener(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                client.removeListener(MatchEndDialog.this);
            }
        });
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------------ ClientListener: keep the buttons current

    @Override
    public void roomChanged(RoomInfo room) {
        refresh();
    }

    @Override
    public void meChanged(UserInfo me) {
        refresh();
    }

    // ------------------------------------------------------------------ rendering

    private void refresh() {
        RoomInfo room = client.room();
        UserInfo me = client.me();
        if (room == null || me == null) {
            return; // MainFrame closes the dialog
        }
        int change = match.result().ratingChangeOf(match.myMark());
        boolean showElo = !match.isSpectating() && match.isRated() && kind != EndKind.ABORTED;
        elo.setText(showElo ? Texts.signed(change) + " Elo  ·  Điểm hiện tại: " + me.rating() : " ");
        elo.setForeground(change > 0 ? Ui.SUCCESS : change < 0 ? Ui.DANGER : Ui.MUTED);

        boolean opponentLeft = !match.isSpectating()
                && room.players().stream().noneMatch(p -> p.id() == match.opponent().id());
        boolean asked = room.rematchRequestedBy(me.id());
        boolean opponentAsked = !match.isSpectating() && room.rematchRequestedBy(match.opponent().id());
        rematchButton.setEnabled(canEverRematch() && room.isFinished() && !opponentLeft && !asked);
        rematchButton.setText(asked ? "Đang chờ đối thủ..." : "Chơi lại (đổi quân)");
        if (opponentLeft) {
            note.setText("Đối thủ đã rời phòng.");
        } else if (opponentAsked && !asked && canEverRematch()) {
            note.setText("Đối thủ muốn chơi lại!");
        } else {
            note.setText(" ");
        }
    }

    private boolean canEverRematch() {
        return !match.isSpectating() && kind != EndKind.ABORTED;
    }

    private String playersText() {
        if (match.isSpectating()) {
            return match.playerX().displayName() + " (X) gặp " + match.playerO().displayName() + " (O)";
        }
        PlayerInfo opponent = match.opponent();
        return "Đối thủ: " + opponent.displayName();
    }

    private static Color accent(EndKind kind) {
        return switch (kind) {
            case WIN -> Ui.SUCCESS;
            case LOSS -> Ui.DANGER;
            case ABORTED -> Ui.WARNING;
            case DRAW, WATCHED -> Ui.INFO;
        };
    }

    private static BadgeIcon.Symbol symbol(EndKind kind) {
        return switch (kind) {
            case WIN -> BadgeIcon.Symbol.STAR;
            case LOSS -> BadgeIcon.Symbol.CROSS;
            case DRAW -> BadgeIcon.Symbol.EQUALS;
            case ABORTED -> BadgeIcon.Symbol.EXCLAIM;
            case WATCHED -> BadgeIcon.Symbol.CHECK;
        };
    }
}
