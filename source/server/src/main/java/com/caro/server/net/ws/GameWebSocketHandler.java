package com.caro.server.net.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.caro.server.config.CaroProperties;
import com.caro.server.net.MessageDispatcher;

/** WebSocket transport (/ws) for the web client. Same protocol as TCP, delegated to {@link MessageDispatcher}. */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final MessageDispatcher dispatcher;
    private final int maxOutboundQueue;
    private final Map<String, WsClientSession> sessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(MessageDispatcher dispatcher, CaroProperties props) {
        this.dispatcher = dispatcher;
        this.maxOutboundQueue = props.tcp().maxOutboundQueue();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        WsClientSession session = new WsClientSession(ws, maxOutboundQueue);
        sessions.put(ws.getId(), session);
        dispatcher.onOpen(session);
        session.start();
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        WsClientSession session = sessions.get(ws.getId());
        if (session != null) {
            dispatcher.onMessage(session, message.getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) throws Exception {
        ws.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        WsClientSession session = sessions.remove(ws.getId());
        if (session != null) {
            session.close();
            dispatcher.onClose(session);
        }
    }
}
