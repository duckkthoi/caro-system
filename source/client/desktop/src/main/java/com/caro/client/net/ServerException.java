package com.caro.client.net;

/**
 * A request that failed: either an ERROR reply from the server (code from docs/protocol.md section 6)
 * or a local problem ({@link #DISCONNECTED}, {@link #TIMEOUT}). The message is meant for the user.
 */
public class ServerException extends RuntimeException {

    public static final String DISCONNECTED = "DISCONNECTED";
    public static final String TIMEOUT = "TIMEOUT";

    private final String code;

    public ServerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Unwraps the CompletionException that CompletableFuture puts around failures. */
    public static String messageOf(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && !(cause instanceof ServerException)) {
            cause = cause.getCause();
        }
        return cause instanceof ServerException ? cause.getMessage() : "Lỗi: " + cause.getMessage();
    }

    public static String codeOf(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && !(cause instanceof ServerException)) {
            cause = cause.getCause();
        }
        return cause instanceof ServerException se ? se.code() : null;
    }
}
