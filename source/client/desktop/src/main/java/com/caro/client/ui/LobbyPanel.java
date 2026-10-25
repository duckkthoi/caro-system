package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.model.OnlineUser;
import com.caro.client.model.QueueStatus;
import com.caro.client.model.RoomInfo;
import com.caro.client.model.UserInfo;

/** Rooms, online players, quick match and playing against the computer. */
final class LobbyPanel extends JPanel implements ClientListener {

    private final GameClient client;
    private final MainFrame frame;

    private final JLabel greeting = Ui.title(" ");
    private final ListTableModel<RoomInfo> roomModel = new ListTableModel<>(
            new String[] {"Phòng", "Chủ phòng", "Trạng thái", "Người chơi", "Xem"},
            r -> new Object[] {r.name() + Texts.roomTags(r), Texts.player(r.host()), Texts.roomStatus(r.status()),
                    r.players().size() + "/2", r.spectatorList().size()});
    private final ListTableModel<OnlineUser> onlineModel = new ListTableModel<>(
            new String[] {"Người chơi", "Elo", "Trạng thái"},
            u -> new Object[] {u.displayName() + " (" + u.username() + ")", u.rating(), Texts.presence(u.status())});
    private final JTable roomTable = table(roomModel);
    private final JTable onlineTable = table(onlineModel);
    private final JButton joinButton = new JButton("Vào phòng");
    private final JButton spectateButton = new JButton("Xem");
    private final JButton inviteButton = new JButton("Mời chơi");
    private final JButton profileButton = new JButton("Xem hồ sơ");
    private final JButton quickButton = new JButton("Tìm trận nhanh");
    private final JLabel queueLabel = Ui.muted("Ghép ngẫu nhiên với người có Elo gần bạn.");
    private static final int QUEUE_TEXT_WIDTH = 520;
    private final Timer queueTimer = new Timer(1000, e -> updateQueueLabel());

    LobbyPanel(GameClient client, MainFrame frame) {
        super(new BorderLayout(0, 8));
        this.client = client;
        this.frame = frame;
        client.addListener(this);
        setBorder(Ui.padding(12));

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.add(greeting, BorderLayout.WEST);
        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        headerButtons.add(Ui.button("Hồ sơ của tôi", () -> frame.showProfile(client.me().id())));
        headerButtons.add(Ui.button("Lịch sử đấu", () -> frame.showHistory(client.me().id(), "Lịch sử đấu của bạn")));
        headerButtons.add(Ui.button("Bảng xếp hạng", frame::showLeaderboard));
        headerButtons.add(Ui.button("Đăng xuất", client::logout)); // the server also takes us out of the queue
        header.add(headerButtons, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // rooms
        JButton createButton = new JButton("Tạo phòng");
        createButton.addActionListener(e -> createRoom(createButton));
        joinButton.addActionListener(e -> joinSelected());
        spectateButton.addActionListener(e -> spectateSelected());
        roomTable.addMouseListener(onDoubleClick(this::joinOrWatchSelected));
        roomTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
        roomTable.getColumnModel().getColumn(3).setMaxWidth(90);
        roomTable.getColumnModel().getColumn(4).setMaxWidth(60);
        JPanel rooms = section("Phòng đang mở", roomTable, createButton, joinButton, spectateButton);

        // online players
        inviteButton.addActionListener(e -> inviteSelected());
        profileButton.addActionListener(e -> {
            OnlineUser u = onlineModel.at(onlineTable.getSelectedRow());
            if (u != null) {
                frame.showProfile(u.id());
            }
        });
        onlineTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
        onlineTable.getColumnModel().getColumn(1).setMaxWidth(70);
        JPanel players = section("Người chơi online", onlineTable, inviteButton, profileButton);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rooms, players);
        split.setResizeWeight(0.58);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        // quick match and the bot
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        quick.setBorder(BorderFactory.createTitledBorder("Tìm trận nhanh"));
        quickButton.addActionListener(e -> toggleQueue());
        quick.add(quickButton);
        quick.add(queueLabel);
        // one above the other, so neither has to squeeze its text
        JPanel south = new JPanel(new GridLayout(0, 1, 0, 4));
        south.add(quick);
        south.add(botPanel());
        add(south, BorderLayout.SOUTH);

        updateButtons();
    }

    // ------------------------------------------------------------------ ClientListener

    @Override
    public void loggedIn(UserInfo me) {
        meChanged(me);
    }

    @Override
    public void meChanged(UserInfo me) {
        greeting.setText("Xin chào, " + me.displayName() + " · Elo " + me.rating());
    }

    @Override
    public void roomsChanged(List<RoomInfo> rooms) {
        roomModel.setRows(rooms, roomTable, RoomInfo::id);
        updateButtons();
    }

    @Override
    public void onlineChanged(List<OnlineUser> users) {
        onlineModel.setRows(users, onlineTable, OnlineUser::id);
        updateButtons();
    }

    @Override
    public void queueChanged(QueueStatus status) {
        boolean waiting = status != null && status.inQueue();
        quickButton.setText(waiting ? "Hủy tìm trận" : "Tìm trận nhanh");
        if (waiting) {
            queueTimer.start();
        } else {
            queueTimer.stop();
        }
        updateQueueLabel();
    }

    // ------------------------------------------------------------------ actions

    private void createRoom(JButton button) {
        JTextField name = new JTextField(24);
        JCheckBox blockTwoEnds = new JCheckBox("Luật chặn hai đầu (5 quân bị chặn cả hai đầu không tính thắng)");
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel("Tên phòng (để trống để dùng tên mặc định):"));
        form.add(name);
        form.add(blockTwoEnds);
        int choice = JOptionPane.showConfirmDialog(this, form, "Tạo phòng", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            Ui.run(this, button, client.createRoom(name.getText(), blockTwoEnds.isSelected()), null);
        }
    }

    private JPanel botPanel() {
        JComboBox<String> level = new JComboBox<>(new String[] {"Dễ", "Khó"});
        JCheckBox blockTwoEnds = new JCheckBox("Chặn hai đầu");
        JButton play = new JButton("Chơi với máy");
        play.addActionListener(e -> Ui.run(this, play,
                client.playBot(level.getSelectedIndex() == 0 ? "EASY" : "HARD", blockTwoEnds.isSelected()), null));
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Chơi với máy (không tính Elo)"));
        panel.add(level);
        panel.add(blockTwoEnds);
        panel.add(play);
        return panel;
    }

    private void joinSelected() {
        RoomInfo room = roomModel.at(roomTable.getSelectedRow());
        if (room != null && room.isWaiting()) {
            Ui.run(this, joinButton, client.joinRoom(room.id()), null);
        }
    }

    private void spectateSelected() {
        RoomInfo room = roomModel.at(roomTable.getSelectedRow());
        if (room != null) {
            Ui.run(this, spectateButton, client.spectate(room.id()), null);
        }
    }

    /** Double click: take the free seat if there is one, otherwise watch. */
    private void joinOrWatchSelected() {
        RoomInfo room = roomModel.at(roomTable.getSelectedRow());
        if (room != null && room.isWaiting()) {
            joinSelected();
        } else {
            spectateSelected();
        }
    }

    private void inviteSelected() {
        OnlineUser user = onlineModel.at(onlineTable.getSelectedRow());
        if (user != null) {
            frame.invite(user);
        }
    }

    private void toggleQueue() {
        if (client.isInQueue()) {
            Ui.run(this, quickButton, client.cancelQuickMatch(), null);
        } else {
            Ui.run(this, quickButton, client.quickMatch(), null);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void updateButtons() {
        RoomInfo room = roomModel.at(roomTable.getSelectedRow());
        joinButton.setEnabled(room != null && room.isWaiting());
        spectateButton.setEnabled(room != null);
        OnlineUser user = onlineModel.at(onlineTable.getSelectedRow());
        boolean notMe = user != null && client.me() != null && user.id() != client.me().id();
        inviteButton.setEnabled(notMe && user.invitable() && !client.isInQueue());
        profileButton.setEnabled(user != null);
    }

    private void updateQueueLabel() {
        QueueStatus q = client.queue();
        if (q == null || !q.inQueue()) {
            queueLabel.setText(Ui.html("Ghép ngẫu nhiên với người có Elo gần bạn.", QUEUE_TEXT_WIDTH));
            updateButtons();
            return;
        }
        long waited = Math.max(0, client.serverNow() - (q.since() == null ? client.serverNow() : q.since()));
        String range = q.ratingRange() == null ? "bất kỳ ai" : "chênh lệch Elo ±" + q.ratingRange();
        queueLabel.setText(Ui.html("Đang tìm đối thủ (" + range + ") · đã chờ " + Texts.clock(waited)
                + " · " + q.queueSize() + " người trong hàng chờ", QUEUE_TEXT_WIDTH));
        updateButtons();
    }

    private static JPanel section(String title, JTable table, JButton... buttons) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JButton b : buttons) {
            bar.add(b);
        }
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private static JTable table(AbstractTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        Ui.wrapCells(table);
        return table;
    }

    static MouseAdapter onDoubleClick(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    action.run();
                }
            }
        };
    }

    /** Read-only table over a list of records; keeps the selected row (by id) when the list is replaced. */
    static final class ListTableModel<T> extends AbstractTableModel {
        private final String[] columns;
        private final Function<T, Object[]> toRow;
        private List<T> rows = List.of();

        ListTableModel(String[] columns, Function<T, Object[]> toRow) {
            this.columns = columns;
            this.toRow = toRow;
        }

        void setRows(List<T> newRows, JTable table, Function<T, Object> idOf) {
            T selected = at(table.getSelectedRow());
            Object selectedId = selected == null ? null : idOf.apply(selected);
            rows = List.copyOf(newRows);
            fireTableDataChanged();
            for (int i = 0; selectedId != null && i < rows.size(); i++) {
                if (selectedId.equals(idOf.apply(rows.get(i)))) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        T at(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            return toRow.apply(rows.get(row))[column];
        }
    }
}
