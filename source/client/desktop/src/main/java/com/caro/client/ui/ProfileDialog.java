package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.caro.client.model.Profile;

/** Public profile of a player: rating, rank and statistics. */
final class ProfileDialog extends JDialog {

    ProfileDialog(MainFrame frame, Profile profile) {
        super(frame, "Hồ sơ người chơi", ModalityType.MODELESS);
        Profile.Stats s = profile.stats();

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 6));
        row(grid, "Tên đăng nhập", "@" + profile.player().username());
        row(grid, "Elo", String.valueOf(profile.player().rating()));
        row(grid, "Hạng", profile.rank() == null ? "Không xếp hạng" : "#" + profile.rank());
        row(grid, "Trạng thái", profile.online() ? "Đang online" : "Offline");
        row(grid, "Tham gia", Texts.date(profile.joinedAt()));
        row(grid, "Số trận", String.valueOf(s.played()));
        row(grid, "Thắng / Thua / Hòa", s.wins() + " / " + s.losses() + " / " + s.draws());
        row(grid, "Tỉ lệ thắng", s.winRate() + "%");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(Ui.button("Lịch sử đấu", () -> frame.showHistory(profile.player().id(),
                "Lịch sử đấu của " + profile.player().displayName())));
        buttons.add(Ui.button("Đóng", this::dispose));

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 14)), 18);
        content.add(Ui.title(profile.player().displayName()), BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(frame);
    }

    private static void row(JPanel grid, String label, String value) {
        grid.add(Ui.muted(label));
        grid.add(new JLabel(value));
    }
}
