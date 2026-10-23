package com.caro.client.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;

import com.caro.client.app.ClientListener;
import com.caro.client.app.GameClient;
import com.caro.client.model.Invitation;
import com.caro.client.model.OnlineUser;
import com.caro.client.model.UserInfo;

/**
 * Invitation dialogs: the ones I receive (accept/decline, with a countdown) and the one I sent (cancel).
 * They are closed by INVITE_CLOSED from the server, never by a local timer, so both sides always agree.
 */
final class Invites implements ClientListener {

    private final GameClient client;
    private final JFrame owner;
    private final Map<String, JDialog> incoming = new HashMap<>();
    private String outgoingId;
    private String outgoingName;
    private JDialog outgoingDialog;

    Invites(GameClient client, JFrame owner) {
        this.client = client;
        this.owner = owner;
        client.addListener(this);
    }

    /** Sends an invitation and shows "waiting for an answer". */
    void invite(OnlineUser user) {
        Ui.run(owner, null, client.invite(user.id()), inviteId -> showOutgoing(inviteId, user.displayName()));
    }

    /** Lets me choose one of the players who can be invited right now. */
    void pickAndInvite() {
        UserInfo me = client.me();
        DefaultListModel<OnlineUser> model = new DefaultListModel<>();
        for (OnlineUser u : client.online()) {
            if (u.invitable() && me != null && u.id() != me.id()) {
                model.addElement(u);
            }
        }
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Hiện không có người chơi nào rảnh để mời.");
            return;
        }
        JList<OnlineUser> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer((l, u, index, selected, focus) -> {
            JLabel label = new JLabel(u.displayName() + " (@" + u.username() + ") · Elo " + u.rating()
                    + " · " + Texts.presence(u.status()));
            label.setBorder(Ui.padding(4));
            label.setOpaque(true);
            label.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });
        int choice = JOptionPane.showConfirmDialog(owner, new JScrollPane(list), "Mời người chơi",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            invite(list.getSelectedValue());
        }
    }

    // ------------------------------------------------------------------ ClientListener

    @Override
    public void invited(Invitation invitation) {
        JDialog dialog = new JDialog(owner, "Lời mời chơi", false);
        JLabel text = new JLabel(Ui.html(invitation.from().displayName() + " (Elo " + invitation.from().rating()
                + ") mời bạn chơi một ván."));
        JLabel countdown = Ui.muted(" ");
        Timer timer = new Timer(250, e -> countdown.setText("Lời mời hết hạn sau "
                + Texts.clock(Math.max(0, invitation.expiresAt() - client.serverNow()))));
        timer.setInitialDelay(0);
        timer.start();

        JButton accept = new JButton("Đồng ý");
        JButton decline = new JButton("Từ chối");
        accept.addActionListener(e -> Ui.run(dialog, accept, client.acceptInvite(invitation.inviteId()),
                room -> closeIncoming(invitation.inviteId())));
        decline.addActionListener(e -> {
            client.declineInvite(invitation.inviteId());
            closeIncoming(invitation.inviteId());
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
            }
        });
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 10)), 16);
        content.add(text, BorderLayout.NORTH);
        content.add(countdown, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(decline);
        buttons.add(accept);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(accept);
        incoming.put(invitation.inviteId(), dialog);
        dialog.setVisible(true);
        owner.toFront();
        dialog.toFront();
    }

    @Override
    public void inviteClosed(String inviteId, String reason) {
        if (incoming.containsKey(inviteId)) {
            closeIncoming(inviteId);
            return;
        }
        if (inviteId.equals(outgoingId)) {
            String name = outgoingName;
            closeOutgoing();
            if (!"ACCEPTED".equals(reason)) {
                JOptionPane.showMessageDialog(owner, Texts.inviteClosed(reason, name), "Lời mời",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    @Override
    public void loggedOut(String reason) {
        for (String id : List.copyOf(incoming.keySet())) {
            closeIncoming(id);
        }
        closeOutgoing();
    }

    // ------------------------------------------------------------------ helpers

    private void showOutgoing(String inviteId, String name) {
        closeOutgoing();
        outgoingId = inviteId;
        outgoingName = name;
        outgoingDialog = new JDialog(owner, "Đã gửi lời mời", false);
        JButton cancel = new JButton("Hủy lời mời");
        cancel.addActionListener(e -> {
            client.cancelInvite(inviteId);
            closeOutgoing();
        });
        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, 10)), 16);
        content.add(new JLabel("Đang chờ " + name + " trả lời..."), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        outgoingDialog.setContentPane(content);
        outgoingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        outgoingDialog.pack();
        outgoingDialog.setLocationRelativeTo(owner);
        outgoingDialog.setVisible(true);
    }

    private void closeIncoming(String inviteId) {
        JDialog dialog = incoming.remove(inviteId);
        if (dialog != null) {
            dialog.dispose();
        }
    }

    private void closeOutgoing() {
        if (outgoingDialog != null) {
            outgoingDialog.dispose();
        }
        outgoingDialog = null;
        outgoingId = null;
        outgoingName = null;
    }
}
