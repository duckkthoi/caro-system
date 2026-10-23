package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;

import com.caro.client.app.BoardModel;
import com.caro.client.model.MatchDetail;

/** Replays a stored match move by move (data from GET_MATCH). */
final class ReplayDialog extends JDialog {

    private final MatchDetail detail;
    private final BoardPanel board = new BoardPanel();
    private final JSlider slider;
    private final JLabel position = new JLabel();
    private final JButton playButton = new JButton("Tự chạy");
    private final Timer autoPlay = new Timer(700, e -> step(1));

    ReplayDialog(Window owner, MatchDetail detail) {
        super(owner, "Xem lại trận đấu", ModalityType.MODELESS);
        this.detail = detail;
        int total = detail.moves().size();
        slider = new JSlider(0, total, total);
        slider.addChangeListener(e -> show(slider.getValue()));

        MatchDetail.Summary m = detail.match();
        JLabel header = Ui.title("X: " + Texts.player(m.playerX()) + "   vs   O: " + Texts.player(m.playerO()));
        JLabel result = Ui.muted(Texts.result(m.result()) + " · " + Texts.reason(m.reason()) + " · "
                + Texts.dateTime(m.endedAt()));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(header, BorderLayout.NORTH);
        top.add(result, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        controls.add(Ui.button("|<", () -> slider.setValue(0)));
        controls.add(Ui.button("<", () -> step(-1)));
        controls.add(position);
        controls.add(Ui.button(">", () -> step(1)));
        controls.add(Ui.button(">|", () -> slider.setValue(slider.getMaximum())));
        controls.add(playButton);
        playButton.addActionListener(e -> toggleAutoPlay());
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(slider, BorderLayout.NORTH);
        bottom.add(controls, BorderLayout.SOUTH);

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 10)), 12);
        content.add(top, BorderLayout.NORTH);
        content.add(board, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        show(total);
    }

    @Override
    public void dispose() {
        autoPlay.stop();
        super.dispose();
    }

    private void step(int delta) {
        int next = slider.getValue() + delta;
        if (next > slider.getMaximum()) {
            toggleAutoPlay();
            return;
        }
        slider.setValue(Math.max(0, next));
    }

    private void toggleAutoPlay() {
        if (autoPlay.isRunning()) {
            autoPlay.stop();
            playButton.setText("Tự chạy");
        } else {
            if (slider.getValue() == slider.getMaximum()) {
                slider.setValue(0);
            }
            autoPlay.start();
            playButton.setText("Dừng");
        }
    }

    private void show(int moveCount) {
        board.setModel(new Position(detail, moveCount));
        position.setText("Nước " + moveCount + "/" + detail.moves().size());
    }

    /** The board after the first {@code count} moves. The winning line only shows on the final position. */
    private record Position(MatchDetail detail, int count) implements BoardModel {

        @Override
        public int size() {
            return 15;
        }

        @Override
        public char cell(int x, int y) {
            for (int i = count - 1; i >= 0; i--) {
                MatchDetail.Move m = detail.moves().get(i);
                if (m.x() == x && m.y() == y) {
                    return m.mark().charAt(0);
                }
            }
            return '.';
        }

        @Override
        public int[] lastMove() {
            if (count == 0) {
                return null;
            }
            MatchDetail.Move m = detail.moves().get(count - 1);
            return new int[] {m.x(), m.y()};
        }

        @Override
        public List<int[]> winLine() {
            boolean last = count == detail.moves().size();
            return last && detail.winLine() != null ? detail.winLine() : List.of();
        }
    }
}
