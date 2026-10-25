package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.app.MatchView;
import com.caro.client.model.ChatMessage;
import com.caro.client.model.MatchResult;
import com.caro.client.model.PlayerInfo;
import com.caro.client.model.RoomInfo;

/** My room, or the room I watch: the board, both players, the turn clock, the match buttons and the chat. */
final class RoomPanel extends JPanel implements ClientListener {

    private final GameClient client;
    private final MainFrame frame;

    private final BoardPanel board = new BoardPanel();
    private final JLabel roomName = Ui.title(" ");
    private final JLabel roomTags = Ui.muted(" ");
    private final JLabel spectators = Ui.muted(" ");
    private final JTextArea chatLog = new JTextArea(); // plain text: a message is never read as HTML
    private final JTextField chatInput = new JTextField();
    private final JButton chatSend = new JButton("Gửi");
    private static final int SIDE_TEXT_WIDTH = 260; // the side column is 290 px wide
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
        roomTags.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(roomTags);
        side.add(Box.createVerticalStrut(10));
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
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        side.add(info);

        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        side.add(buttons);
        side.add(Box.createVerticalStrut(8));
        spectators.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(spectators);
        side.add(Box.createVerticalStrut(8));
        JPanel chat = chatPanel();
        chat.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(chat); // takes the remaining height
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

    private JPanel chatPanel() {
        chatLog.setEditable(false);
        chatLog.setLineWrap(true);
        chatLog.setWrapStyleWord(true);
        chatLog.setRows(8);
        chatInput.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, AttributeSet a)
                    throws BadLocationException {
                if (str != null && getLength() + str.length() <= 200) { // the server's limit
                    super.insertString(offs, str, a);
                }
            }
        });
        chatInput.addActionListener(e -> sendChat());
        chatSend.addActionListener(e -> sendChat());

        JPanel input = new JPanel(new BorderLayout(6, 0));
        input.add(chatInput, BorderLayout.CENTER);
        input.add(chatSend, BorderLayout.EAST);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Trò chuyện"));
        panel.add(new JScrollPane(chatLog, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        panel.add(input, BorderLayout.SOUTH);
        return panel;
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

    @Override
    public void chatChanged(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage m : messages) {
            text.append(m.from().displayName()).append(m.spectator() ? " (người xem)" : "")
                    .append(" · ").append(Texts.time(m.sentAt())).append('\n')
                    .append(m.text()).append("\n\n");
        }
        chatLog.setText(text.toString());
        chatLog.setCaretPosition(chatLog.getDocument().getLength());
    }

    // ------------------------------------------------------------------ rendering

    private void refresh() {
        RoomInfo room = client.room();
        MatchView match = client.match();
        if (room == null) {
            ticker.stop();
            return;
        }
        boolean watching = client.isSpectating();
        roomName.setText(Ui.html(room.name(), SIDE_TEXT_WIDTH));
        roomTags.setText(Ui.html(tagsText(room, watching), SIDE_TEXT_WIDTH));
        List<PlayerInfo> watchers = room.spectatorList();
        spectators.setText(watchers.isEmpty() ? " " : Ui.html("Người xem (" + watchers.size() + "): "
                + watchers.stream().map(PlayerInfo::displayName).collect(Collectors.joining(", ")), SIDE_TEXT_WIDTH));
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
        showButtons(room, watching);
        boolean asked = room.rematchRequestedBy(myId);
        rematchButton.setEnabled(room.isFinished() && !asked);
        rematchButton.setText(asked ? "Đã gửi yêu cầu chơi lại" : "Chơi lại (đổi quân)");

        status.setText(Ui.htmlCentered(watching ? spectatorText(room, match) : statusText(room, match, myId),
                SIDE_TEXT_WIDTH));
        if (live) {
            ticker.start();
        } else {
            ticker.stop();
        }
        refreshClock();
    }

    /** Only the buttons that make sense now, without gaps for the hidden ones. */
    private void showButtons(RoomInfo room, boolean watching) {
        buttons.removeAll();
        leaveButton.setText(watching ? "Thôi xem" : "Rời phòng");
        if (watching) {
            buttons.add(leaveButton);
            buttons.revalidate();
            buttons.repaint();
            return;
        }
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

    private static String tagsText(RoomInfo room, boolean watching) {
        StringBuilder tags = new StringBuilder();
        if (watching) {
            tags.append("Bạn đang xem · ");
        }
        if (room.botLevel() != null) {
            tags.append("Chơi với máy (").append(Texts.botLevel(room.botLevel())).append(") · ");
        }
        if (room.blockTwoEnds()) {
            tags.append(Texts.BLOCK_TWO_ENDS).append(" · ");
        }
        return tags.isEmpty() ? " " : tags.substring(0, tags.length() - 3);
    }

    private String spectatorText(RoomInfo room, MatchView match) {
        if (room.isWaiting() || match == null) {
            return "Phòng đang chờ người chơi thứ hai.";
        }
        if (match.isFinished()) {
            MatchResult r = match.result();
            return r == null ? "Trận đấu đã kết thúc" : Texts.result(r.result()) + ": " + Texts.reason(r.reason());
        }
        if (match.isPaused()) {
            long left = match.graceDeadline() == null ? 0 : match.graceDeadline() - client.serverNow();
            return "Một người chơi mất kết nối.\nChờ quay lại: " + Texts.clock(Math.max(0, left));
        }
        PlayerInfo onTurn = "X".equals(match.turn()) ? match.playerX() : match.playerO();
        return "Đến lượt " + onTurn.displayName() + " (" + match.turn() + ")";
    }

    private String statusText(RoomInfo room, MatchView match, long myId) {
        if (room.isWaiting()) {
            return "Đang chờ đối thủ vào phòng...\nBạn có thể mời một người đang online.";
        }
        if (match == null) {
            return " ";
        }
        if (match.isFinished()) {
            String text = resultText(match.result(), match.myMark(), match.isRated(), myId);
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

    private static String resultText(MatchResult r, String myMark, boolean rated, long myId) {
        if (r == null) {
            return "Trận đấu đã kết thúc";
        }
        String reason = Texts.reason(r.reason());
        int change = r.ratingChangeOf(myMark);
        String elo = rated ? " (" + Texts.signed(change) + " Elo)" : "";
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
            status.setText(Ui.htmlCentered(client.isSpectating() ? spectatorText(room, match)
                    : statusText(room, match, client.me().id()), SIDE_TEXT_WIDTH));
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

    private void sendChat() {
        String text = chatInput.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        Ui.run(this, chatSend, client.sendChat(text), ok -> chatInput.setText(""));
    }

    private void leave() {
        MatchView match = client.match();
        boolean live = match != null && !match.isFinished() && !client.room().isWaiting() && !client.isSpectating();
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
        private static final int CARD_TEXT_WIDTH = 190; // card width minus the mark and the padding
        private final Color markColor;

        PlayerCard(String mark) {
            super(new BorderLayout(10, 0));
            this.markColor = Ui.markColor(mark);
            JLabel markLabel = new JLabel(mark, SwingConstants.CENTER);
            markLabel.setFont(markLabel.getFont().deriveFont(Font.BOLD, 30f));
            markLabel.setForeground(markColor);
            markLabel.setPreferredSize(new Dimension(40, 40));
            name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            text.add(name);
            text.add(detail);
            add(markLabel, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setActive(false);
        }

        void show(PlayerInfo p, boolean me, boolean active) {
            name.setText(Ui.html(p == null ? "(trống)" : p.displayName() + (me ? "  (bạn)" : ""), CARD_TEXT_WIDTH));
            detail.setText(Ui.html(p == null ? " " : p.id() == RoomInfo.BOT_ID ? "Máy tính"
                    : "@" + p.username() + " · Elo " + p.rating(), CARD_TEXT_WIDTH));
            setActive(active);
        }

        /** As tall as the (possibly wrapped) text needs, never stretched by the BoxLayout. */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private void setActive(boolean active) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(active ? markColor : new Color(0xDD, 0xDD, 0xDD), active ? 3 : 1, true),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        }
    }
}
