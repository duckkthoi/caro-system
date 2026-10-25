package com.caro.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.caro.client.model.PlayerInfo;
import com.caro.client.model.RoomInfo;

/** Vietnamese labels for the codes sent by the server. */
final class Texts {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    static final String BLOCK_TWO_ENDS = "Luật chặn hai đầu";

    private Texts() {
    }

    static String roomStatus(String status) {
        return switch (status) {
            case "WAITING" -> "Đang chờ";
            case "PLAYING" -> "Đang đấu";
            case "FINISHED" -> "Vừa đấu xong";
            default -> status;
        };
    }

    static String presence(String status) {
        return switch (status) {
            case "IDLE" -> "Rảnh";
            case "IN_QUEUE" -> "Đang tìm trận";
            case "IN_ROOM" -> "Trong phòng";
            case "PLAYING" -> "Đang đấu";
            case "SPECTATING" -> "Đang xem";
            default -> status;
        };
    }

    static String botLevel(String level) {
        return "EASY".equals(level) ? "Dễ" : "HARD".equals(level) ? "Khó" : level;
    }

    /** Short labels after a room name: against the bot, optional rule. */
    static String roomTags(RoomInfo room) {
        StringBuilder tags = new StringBuilder();
        if (room.botLevel() != null) {
            tags.append("  [Máy · ").append(botLevel(room.botLevel())).append("]");
        }
        if (room.blockTwoEnds()) {
            tags.append("  [Chặn hai đầu]");
        }
        return tags.toString();
    }

    static String outcome(String outcome) {
        return switch (outcome) {
            case "WIN" -> "Thắng";
            case "LOSS" -> "Thua";
            case "DRAW" -> "Hòa";
            case "ABORTED" -> "Bị hủy";
            default -> outcome;
        };
    }

    static String reason(String reason) {
        return switch (reason) {
            case "FIVE_IN_ROW" -> "5 quân liên tiếp";
            case "BOARD_FULL" -> "Kín bàn cờ";
            case "RESIGN" -> "Đầu hàng";
            case "TIMEOUT" -> "Hết giờ";
            case "DISCONNECT" -> "Mất kết nối";
            case "BOTH_DISCONNECTED" -> "Cả hai mất kết nối";
            default -> reason;
        };
    }

    static String result(String result) {
        return switch (result) {
            case "X_WIN" -> "X thắng";
            case "O_WIN" -> "O thắng";
            case "DRAW" -> "Hòa";
            case "ABORTED" -> "Bị hủy";
            default -> result;
        };
    }

    static String inviteClosed(String reason, String who) {
        return switch (reason) {
            case "DECLINED" -> who + " đã từ chối lời mời.";
            case "EXPIRED" -> who + " không trả lời, lời mời đã hết hạn.";
            case "CANCELLED" -> "Lời mời đã bị hủy.";
            case "UNAVAILABLE" -> who + " không còn sẵn sàng.";
            default -> "Lời mời đã đóng.";
        };
    }

    static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    /** HH:mm of an epoch-ms time (chat). */
    static String time(long epochMs) {
        return TIME.format(Instant.ofEpochMilli(epochMs));
    }

    static String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    static String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant);
    }

    static String player(PlayerInfo p) {
        return p == null ? "?" : p.displayName() + " (" + p.rating() + ")";
    }

    /** m:ss */
    static String clock(long millis) {
        long seconds = (millis + 999) / 1000; // 0.2 s left still shows 0:01
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
