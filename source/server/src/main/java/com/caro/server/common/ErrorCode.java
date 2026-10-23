package com.caro.server.common;

/** Error codes shared by the game protocol and the REST API (see docs/protocol.md, section 6). */
public enum ErrorCode {
    BAD_REQUEST(400),
    UNKNOWN_TYPE(400),
    UNAUTHORIZED(401),
    ALREADY_AUTHENTICATED(409),
    VALIDATION_ERROR(400),
    USERNAME_TAKEN(409),
    INVALID_CREDENTIALS(401),
    ACCOUNT_LOCKED(403),
    ALREADY_IN_ROOM(409),
    NOT_IN_ROOM(409),
    ROOM_NOT_FOUND(404),
    ROOM_FULL(409),
    MATCH_NOT_FOUND(404),
    MATCH_NOT_IN_PROGRESS(409),
    NOT_YOUR_TURN(409),
    INVALID_POSITION(400),
    CELL_OCCUPIED(409),
    FORBIDDEN(403),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
