package com.caro.client.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** The one ObjectMapper of the client. Unknown fields are ignored, so the server may add fields freely. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    /** {"type","reqId","data"}, see docs/protocol.md section 2. */
    static String envelope(String type, String reqId, Object data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("reqId", reqId);
        message.put("data", data == null ? Map.of() : data);
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + type, e);
        }
    }

    static JsonNode parse(String line) {
        try {
            return MAPPER.readTree(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        return MAPPER.convertValue(node, type);
    }

    public static <T> T convert(JsonNode node, TypeReference<T> type) {
        return MAPPER.convertValue(node, type);
    }
}
