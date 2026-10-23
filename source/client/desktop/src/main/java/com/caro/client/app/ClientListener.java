package com.caro.client.app;

import java.util.List;

import com.caro.client.model.Invitation;
import com.caro.client.model.OnlineUser;
import com.caro.client.model.QueueStatus;
import com.caro.client.model.RoomInfo;
import com.caro.client.model.UserInfo;

/** Callbacks of {@link GameClient}. All run on the Swing thread. Implement only what you need. */
public interface ClientListener {

    enum ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    default void connectionChanged(ConnectionStatus status, String message) {
    }

    default void loggedIn(UserInfo me) {
    }

    /** Back to the login screen. {@code reason} explains why (null after a normal logout). */
    default void loggedOut(String reason) {
    }

    /** My own info changed, e.g. new rating after a match. */
    default void meChanged(UserInfo me) {
    }

    default void roomsChanged(List<RoomInfo> rooms) {
    }

    default void onlineChanged(List<OnlineUser> users) {
    }

    /** Entered, updated or left (null) my room. */
    default void roomChanged(RoomInfo room) {
    }

    /** Any change of the current match (also a new match). Null when there is none. */
    default void matchChanged(MatchView match) {
    }

    default void queueChanged(QueueStatus status) {
    }

    default void invited(Invitation invitation) {
    }

    /** reason: ACCEPTED | DECLINED | CANCELLED | EXPIRED | UNAVAILABLE */
    default void inviteClosed(String inviteId, String reason) {
    }

    /** A short message worth showing to the user ("Đối thủ mất kết nối..."). */
    default void notice(String text) {
    }
}
