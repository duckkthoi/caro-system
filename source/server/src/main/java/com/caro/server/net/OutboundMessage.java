package com.caro.server.net;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The envelope {"type", "reqId", "data"} for messages sent by the server. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboundMessage(ServerMessageType type, String reqId, Object data) {
}
