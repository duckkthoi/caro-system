package com.caro.server.net.tcp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.caro.server.net.ClientSession;

/** A desktop client connected over a raw TCP socket. One JSON message per line. */
class TcpClientSession extends ClientSession {

    private final Socket socket;
    private final Writer out;

    TcpClientSession(Socket socket, int maxOutboundQueue) throws IOException {
        super("tcp", String.valueOf(socket.getRemoteSocketAddress()), maxOutboundQueue);
        this.socket = socket;
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    protected void write(String json) throws IOException {
        out.write(json);
        out.write('\n'); // frame delimiter
        out.flush();
    }

    @Override
    protected void closeTransport() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
