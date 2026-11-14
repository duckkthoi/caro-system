package com.caro.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpTest {

    @Test
    void trustsTheForwardedAddressOnlyFromALocalProxy() {
        assertThat(ClientIp.resolve("127.0.0.1", "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientIp.resolve("172.18.0.4", "203.0.113.9")).isEqualTo("203.0.113.9"); // docker network
        assertThat(ClientIp.resolve("198.51.100.20", "203.0.113.9")).isEqualTo("198.51.100.20"); // made up
    }

    @Test
    void usesThePeerWhenThereIsNoHeader() {
        assertThat(ClientIp.resolve("127.0.0.1", null)).isEqualTo("127.0.0.1");
        assertThat(ClientIp.resolve("198.51.100.20", " ")).isEqualTo("198.51.100.20");
    }
}
