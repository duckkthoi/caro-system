package com.caro.server.net.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.caro.server.common.ErrorBody;
import com.caro.server.common.ErrorCode;
import com.caro.server.config.CaroProperties;
import com.caro.server.net.MessageDispatcher;
import com.caro.server.net.ServerMessageType;
import com.caro.server.net.SessionRegistry;

/**
 * Raw TCP game server for the desktop client.
 *
 * <p>Threading model: one platform thread blocks in {@code accept()}; every accepted connection gets its own
 * <b>virtual thread</b> that blocks in {@code read()}. Virtual threads are cheap (no 1 MB stack each), so the
 * simple "one thread per connection" style still scales to thousands of clients.
 *
 * <p>Framing: TCP is a byte stream, not a message stream, so messages are separated by '\n'
 * (see docs/protocol.md, section 1). Lines longer than caro.tcp.max-line-length are rejected.
 */
@Component
public class TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final MessageDispatcher dispatcher;
    private final SessionRegistry registry;
    private final CaroProperties.Tcp config;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public TcpServer(MessageDispatcher dispatcher, SessionRegistry registry, CaroProperties props) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.config = props.tcp();
    }

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(config.port());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open TCP port " + config.port(), e);
        }
        running = true;
        Thread.ofPlatform().name("tcp-acceptor").daemon().start(this::acceptLoop);
        log.info("TCP game server listening on port {}", getLocalPort());
    }

    @Override
    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // shutting down anyway
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** The actual port (useful when configured with port 0 in tests). */
    public int getLocalPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true); // small messages: send immediately, don't wait to batch (Nagle)
                Thread.ofVirtual().name("tcp-reader-" + socket.getPort()).start(() -> serve(socket));
            } catch (IOException e) {
                if (running) {
                    log.warn("accept() failed", e);
                }
            }
        }
    }

    private void serve(Socket socket) {
        TcpClientSession session;
        try {
            session = new TcpClientSession(socket, config.maxOutboundQueue());
        } catch (IOException e) {
            closeQuietly(socket);
            return;
        }
        dispatcher.onOpen(session);
        session.start();
        boolean flushBeforeClose = false;
        try (Reader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = readLine(in, config.maxLineLength())) != null) {
                if (!line.isBlank()) {
                    dispatcher.onMessage(session, line);
                }
            }
        } catch (LineTooLongException e) {
            registry.send(session, ServerMessageType.ERROR, null, new ErrorBody(ErrorCode.BAD_REQUEST,
                    "Thông điệp dài quá " + config.maxLineLength() + " ký tự"));
            flushBeforeClose = true;
        } catch (IOException e) {
            // connection reset / closed by us: normal end of a session
        } finally {
            if (flushBeforeClose) {
                session.closeAfterFlush();
            } else {
                session.close();
            }
            dispatcher.onClose(session);
        }
    }

    /**
     * Reads one '\n'-terminated line (a trailing '\r' from telnet is dropped).
     *
     * @return the line, or null at end of stream
     */
    static String readLine(Reader in, int maxLength) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int last = line.length() - 1;
                if (last >= 0 && line.charAt(last) == '\r') {
                    line.setLength(last);
                }
                return line.toString();
            }
            if (line.length() >= maxLength) {
                throw new LineTooLongException();
            }
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    static class LineTooLongException extends IOException {
    }
}
