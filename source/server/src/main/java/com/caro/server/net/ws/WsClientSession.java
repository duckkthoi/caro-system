package com.caro.server.net.ws;

import java.io.IOException;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.caro.server.net.ClientSession;

/** A browser connected over WebSocket. One JSON message per text frame. */
class WsClientSession extends ClientSession {

    private final WebSocketSession ws;

    WsClientSession(WebSocketSession ws, int maxOutboundQueue) {
        super("ws", String.valueOf(ws.getRemoteAddress()), maxOutboundQueue);
        this.ws = ws;
    }

    @Override
    protected void write(String json) throws IOException {
        // safe: only the writer thread calls this (WebSocketSession.sendMessage is not thread-safe)
        ws.sendMessage(new TextMessage(json));
    }

    @Override
    protected void closeTransport() {
        try {
            if (ws.isOpen()) {
                ws.close();
            }
        } catch (IOException ignored) {
            // already closed
        }
    }
}
