package com.caro.server.common;

/** Expected business error. Turned into an ERROR message (socket) or a JSON error body (REST). */
public class AppException extends RuntimeException {

    private final ErrorCode code;

    public AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
