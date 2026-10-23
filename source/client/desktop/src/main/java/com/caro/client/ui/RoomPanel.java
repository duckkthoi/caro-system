package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.app.MatchView;
import com.caro.client.model.MatchResult;
import com.caro.client.model.PlayerInfo;
import com.caro.client.model.RoomInfo;

/** My room: the board, both players, the turn clock and the match buttons. */
final class RoomPanel extends JPanel implements ClientListener {

    private final GameClient client;
    private final MainFrame frame;

    private final BoardPanel board = new BoardPanel();
    private final JLabel roomName = Ui.title(" ");
    private final PlayerCard cardX = new PlayerCard("X");
    private final PlayerCard cardO = new PlayerCard("O");
    private final JLabel clock = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JButton resignButton = new JButton("Đầu hàng");
    private final JButton rematchButton = new JButton("Chơi lại");
    private final JButton inviteButton = new JButton("Mời người chơi");
    private final JButton leaveButton = new JButton("Rời phòng");
    private final JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
    private final Timer ticker = new Timer(250, e -> refreshClock());
    private boolean movePending;

    RoomPanel(GameClient client, MainFrame frame) {
        super(new BorderLayout(12, 0));
        this.client = client;
        this.frame = frame;
        client.addListener(this);
        setBorder(Ui.padding(12));

        board.onCellClicked(this::play);
        add(board, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(290, 10));
        roomName.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(roomName);
        side.add(Box.createVerticalStrut(12));
        for (PlayerCard card : new PlayerCard[] {cardX, cardO}) {
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(card);
            side.add(Box.createVerticalStrut(8));
        }

        clock.setFont(clock.getFont().deriveFont(Font.BOLD, 40f));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 15f));
        JPanel info = new JPanel(new BorderLayout(0, 6));
        info.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        info.add(clock, BorderLayout.NORTH);
        info.add(status, BorderLayout.CENTER);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        side.add(info);

        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        side.add(buttons);
        side.add(Box.createVerticalGlue());
        add(side, BorderLayout.EAST);

        resignButton.addActionListener(e -> {
            if (Ui.confirm(this, "Bạn chắc chắn muốn đầu hàng?")) {
                Ui.run(this, resignButton, client.resign(), null);
            }
        });
        rematchButton.addActionListener(e -> Ui.run(this, rematchButton, client.rematch(), null));
        inviteButton.addActionListener(e -> frame.pickAndInvite());
        leaveButton.addActionListener(e -> leave());
    }

    // ------------------------------------------------------------------ ClientListener

    @Override
    public void roomChanged(RoomInfo room) {
        refresh();
    }

    @Override
    public void matchChanged(MatchView match) {
        refresh();
    }

    // ------------------------------------------------------------------ rendering

    private void refresh() {
        RoomInfo room = client.room();
        MatchView match = client.match();
        if (room == null) {
            ticker.stop();
            return;
        }
        roomName.setText(room.name());
        board.setModel(match);
        boolean myTurn = match != null && match.isMyTurn() && !movePending;
        board.setInteractive(myTurn, match == null ? null : match.myMark());

        boolean live = match != null && !match.isFinished() && !room.isWaiting();
        if (live) {
            cardX.show(match.playerX(), isMe(match.playerX()), "X".equals(match.turn()) && match.isInProgress());
            cardO.show(match.playerO(), isMe(match.playerO()), "O".equals(match.turn()) && match.isInProgress());
        } else {
            showRoomPlayers(room, match);
        }

        long myId = client.me() == null ? -1 : client.me().id();
        resignButton.setEnabled(live);
        showButtons(room);
        boolean asked = room.rematchRequestedBy(myId);
        rematchButton.setEnabled(room.isFinished() && !asked);
        rematchButton.setText(asked ? "Đã gửi yêu cầu chơi lại" : "Chơi lại (đổi quân)");

        status.setText(Ui.html(statusText(room, match, myId)));
        if (live) {
            ticker.start();
        } else {
            ticker.stop();
        }
        refreshClock();
    }

    /** Only the buttons that make sense now, without gaps for the hidden ones. */
    private void showButtons(RoomInfo room) {
        buttons.removeAll();
        if (room.isWaiting()) {
            buttons.add(inviteButton);
        } else if (room.isFinished()) {
            buttons.add(rematchButton);
        } else {
            buttons.add(resignButton);
        }
        buttons.add(leaveButton);
        buttons.revalidate();
        buttons.repaint();
    }

    private void showRoomPlayers(RoomInfo room, MatchView match) {
        if (match != null && !room.isWaiting()) {
            // finished: keep each player next to the mark they just played, with the new rating from the room
            PlayerInfo x = latest(room, match.playerX());
            PlayerInfo o = latest(room, match.playerO());
            cardX.show(x, isMe(x), false);
            cardO.show(o, isMe(o), false);
            return;
        }
        PlayerInfo host = room.host();
        PlayerInfo other = room.players().size() > 1 ? room.players().get(1) : null;
        cardX.show(host, isMe(host), false);
        cardO.show(other, isMe(other), false);
    }

    private String statusText(RoomInfo room, MatchView match, long myId) {
        if (room.isWaiting()) {
            return "Đang chờ đối thủ vào phòng...\nBạn có thể mời một người đang online.";
        }
        if (match == null) {
            return " ";
        }
        if (match.isFinished()) {
            String text = resultText(match.result(), match.myMark(), myId);
            long opponentId = match.opponent().id();
            if (room.rematchRequestedBy(opponentId) && !room.rematchRequestedBy(myId)) {
                text += "\nĐối thủ muốn chơi lại!";
            } else if (room.rematchRequestedBy(myId)) {
                text += "\nĐang chờ đối thủ đồng ý chơi lại...";
            }
            return text;
        }
        if (match.isPaused()) {
            long left = match.graceDeadline() == null ? 0 : match.graceDeadline() - client.serverNow();
            return "Đối thủ mất kết nối.\nChờ đối thủ quay lại: " + Texts.clock(Math.max(0, left));
        }
        return match.isMyTurn() ? "Đến lượt bạn" : "Đang chờ đối thủ đi...";
    }

    private static String resultText(MatchResult r, String myMark, long myId) {
        if (r == null) {
            return "Trận đấu đã kết thúc";
        }
        String reason = Texts.reason(r.reason());
        int change = r.ratingChangeOf(myMark);
        String elo = " (" + Texts.signed(change) + " Elo)";
        return switch (r.result()) {
            case "ABORTED" -> "Trận bị hủy: " + reason + "\nKhông tính điểm.";
            case "DRAW" -> "Hòa: " + reason + elo;
            default -> (r.winnerId() != null && r.winnerId() == myId ? "Bạn thắng! " : "Bạn thua. ")
                    + reason + elo;
        };
    }

    private void refreshClock() {
        MatchView match = client.match();
        RoomInfo room = client.room();
        if (match == null || room == null || match.isFinished() || room.isWaiting()) {
            clock.setText(" ");
            return;
        }
        long left = match.turnTimeLeftMs(client.serverNow());
        clock.setText(Texts.clock(left));
        clock.setForeground(left <= 10_000 && match.isInProgress() ? Ui.DANGER : getForeground());
        if (match.isPaused()) {
            status.setText(Ui.html(statusText(room, match, client.me().id())));
        }
    }

    // ------------------------------------------------------------------ actions

    private void play(int x, int y) {
        MatchView match = client.match();
        if (movePending || match == null || !match.canPlay(x, y)) {
            return;
        }
        movePending = true;
        board.setInteractive(false, null);
        client.move(x, y).whenComplete((ok, error) -> {
            movePending = false;
            if (error != null) {
                Ui.showError(this, error);
            }
            refresh();
        });
    }

    private void leave() {
        MatchView match = client.match();
        boolean live = match != null && !match.isFinished() && !client.room().isWaiting();
        if (live && !Ui.confirm(this, "Rời phòng khi đang đấu sẽ bị tính là thua. Tiếp tục?")) {
            return;
        }
        Ui.run(this, leaveButton, client.leaveRoom(), null);
    }

    /** The room has the ratings after the match; the match only has the ones from its start. */
    private static PlayerInfo latest(RoomInfo room, PlayerInfo p) {
        return room.players().stream().filter(r -> r.id() == p.id()).findFirst().orElse(p);
    }

    private boolean isMe(PlayerInfo p) {
        return p != null && client.me() != null && p.id() == client.me().id();
    }

    /** One player: mark, name, rating; highlighted while it is their turn. */
    private static final class PlayerCard extends JPanel {
        private final JLabel name = new JLabel(" ");
        private final JLabel detail = Ui.muted(" ");
        private final Color markColor;

        PlayerCard(String mark) {
            super(new BorderLayout(10, 0));
            this.markColor = Ui.markColor(mark);
            JLabel markLabel = new JLabel(mark, SwingConstants.CENTER);
            markLabel.setFont(markLabel.getFont().deriveFont(Font.BOLD, 30f));
            markLabel.setForeground(markColor);
            markLabel.setPreferredSize(new Dimension(40, 40));
            name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            text.add(name);
            text.add(detail);
            add(markLabel, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            setActive(false);
        }

        void show(PlayerInfo p, boolean me, boolean active) {
            name.setText(p == null ? "(trống)" : p.displayName() + (me ? "  (bạn)" : ""));
            detail.setText(p == null ? " " : "@" + p.username() + " · Elo " + p.rating());
            setActive(active);
        }

        private void setActive(boolean active) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? markColor : new Color(0xDD, 0xDD, 0xDD), active ? 3 : 1, true),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        }
    }
}
