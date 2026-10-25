package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import com.caro.client.app.GameClient;
import com.caro.client.model.HistoryItem;
import com.caro.client.model.Page;
import com.caro.client.ui.LobbyPanel.ListTableModel;

/** Match history of one player, newest first, with paging and replay. */
final class HistoryDialog extends JDialog {

    private static final int PAGE_SIZE = 15;

    private final GameClient client;
    private final long userId;
    private final ListTableModel<HistoryItem> model = new ListTableModel<>(
            new String[] {"Thời gian", "Đối thủ", "Quân", "Kết quả", "Lý do", "Elo", "Số nước"},
            h -> new Object[] {Texts.dateTime(h.endedAt()), Texts.player(h.opponent()), h.mark(),
                    Texts.outcome(h.outcome()), Texts.reason(h.reason()), Texts.signed(h.ratingChange()),
                    h.totalMoves()});
    private final JTable table = new JTable(model);
    private final JLabel pageLabel = new JLabel();
    private final JButton prev = new JButton("< Trang trước");
    private final JButton next = new JButton("Trang sau >");
    private final JButton replay = new JButton("Xem lại trận");
    private int page;

    HistoryDialog(Window owner, GameClient client, long userId, String title) {
        super(owner, title, ModalityType.MODELESS);
        this.client = client;
        this.userId = userId;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        Ui.wrapCells(table);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(e -> replay.setEnabled(table.getSelectedRow() >= 0));
        table.addMouseListener(LobbyPanel.onDoubleClick(this::replaySelected));
        replay.setEnabled(false);
        replay.addActionListener(e -> replaySelected());
        prev.addActionListener(e -> load(page - 1));
        next.addActionListener(e -> load(page + 1));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.add(prev);
        bar.add(pageLabel);
        bar.add(next);
        bar.add(replay);

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 8)), 12);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(820, 420));
        content.add(scroll, BorderLayout.CENTER);
        content.add(bar, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        load(0);
    }

    private void load(int requested) {
        Ui.run(this, null, client.history(userId, requested, PAGE_SIZE), this::show);
    }

    private void show(Page<HistoryItem> result) {
        page = result.page();
        model.setRows(result.items(), table, HistoryItem::matchId);
        int pages = Math.max(1, result.totalPages());
        pageLabel.setText("Trang " + (page + 1) + "/" + pages + " · " + result.totalItems() + " trận");
        prev.setEnabled(page > 0);
        next.setEnabled(page + 1 < pages);
    }

    private void replaySelected() {
        HistoryItem item = model.at(table.getSelectedRow());
        if (item != null) {
            Ui.run(this, replay, client.matchDetail(item.matchId()),
                    detail -> new ReplayDialog(this, detail).setVisible(true));
        }
    }
}
