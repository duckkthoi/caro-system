package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import com.caro.client.app.GameClient;
import com.caro.client.model.LeaderboardEntry;
import com.caro.client.model.Page;
import com.caro.client.ui.LobbyPanel.ListTableModel;

/** Players ranked by Elo. */
final class LeaderboardDialog extends JDialog {

    private static final int PAGE_SIZE = 20;

    private final GameClient client;
    private final MainFrame frame;
    private final ListTableModel<LeaderboardEntry> model = new ListTableModel<>(
            new String[] {"Hạng", "Người chơi", "Elo", "Online"},
            e -> new Object[] {e.rank(), e.player().displayName() + " (@" + e.player().username() + ")",
                    e.player().rating(), e.online() ? "Có" : ""});
    private final JTable table = new JTable(model);
    private final JLabel pageLabel = new JLabel();
    private final JButton prev = new JButton("<");
    private final JButton next = new JButton(">");
    private final JButton profile = new JButton("Xem hồ sơ");
    private int page;

    LeaderboardDialog(MainFrame frame, GameClient client) {
        super(frame, "Bảng xếp hạng", ModalityType.MODELESS);
        this.client = client;
        this.frame = frame;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        Ui.wrapCells(table);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(80);
        table.getColumnModel().getColumn(3).setMaxWidth(70);
        table.getSelectionModel().addListSelectionListener(e -> profile.setEnabled(table.getSelectedRow() >= 0));
        table.addMouseListener(LobbyPanel.onDoubleClick(this::openProfile));
        profile.setEnabled(false);
        profile.addActionListener(e -> openProfile());
        prev.addActionListener(e -> load(page - 1));
        next.addActionListener(e -> load(page + 1));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.add(prev);
        bar.add(pageLabel);
        bar.add(next);
        bar.add(profile);

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 8)), 12);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 480));
        content.add(scroll, BorderLayout.CENTER);
        content.add(bar, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(frame);
        load(0);
    }

    private void load(int requested) {
        Ui.run(this, null, client.leaderboard(requested, PAGE_SIZE), this::show);
    }

    private void show(Page<LeaderboardEntry> result) {
        page = result.page();
        model.setRows(result.items(), table, e -> e.player().id());
        int pages = Math.max(1, result.totalPages());
        pageLabel.setText("Trang " + (page + 1) + "/" + pages);
        prev.setEnabled(page > 0);
        next.setEnabled(page + 1 < pages);
    }

    private void openProfile() {
        LeaderboardEntry entry = model.at(table.getSelectedRow());
        if (entry != null) {
            frame.showProfile(entry.player().id());
        }
    }
}
