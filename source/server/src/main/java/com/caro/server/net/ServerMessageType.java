package com.caro.server.net;

/** Message types sent by the server (docs/protocol.md, sections 2 and 4). */
public enum ServerMessageType {
    OK,
    ERROR,
    PONG,
    ROOM_LIST,
    ROOM_UPDATE,
    MATCH_START,
    MOVE_APPLIED,
    MATCH_END,
    MATCH_STATE,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    KICKED,
    ONLINE_USERS,
    QUEUE_STATUS,
    INVITED,
    INVITE_CLOSED,
    CHAT_MESSAGE,
    SPECTATE_ENDED
}
