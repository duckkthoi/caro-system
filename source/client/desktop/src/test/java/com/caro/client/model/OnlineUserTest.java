package com.caro.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.caro.client.net.Json;

class OnlineUserTest {

    @Test
    void readsRoleFromTheOnlineList() throws Exception {
        OnlineUser u = Json.MAPPER.readValue(
                "{\"id\":1,\"username\":\"admin\",\"displayName\":\"Admin\",\"rating\":1000,"
                        + "\"role\":\"ADMIN\",\"status\":\"IDLE\"}", OnlineUser.class);
        assertEquals("ADMIN", u.role());
        assertTrue(u.isAdmin());
    }

    @Test
    void adminsAreNeverInvitable() {
        assertFalse(new OnlineUser(1, "admin", "Admin", 1000, "ADMIN", "IDLE").invitable());
        assertTrue(new OnlineUser(2, "bob", "Bob", 1000, "PLAYER", "IDLE").invitable());
        assertTrue(new OnlineUser(3, "cat", "Cat", 1000, "PLAYER", "IN_QUEUE").invitable());
        assertFalse(new OnlineUser(4, "dan", "Dan", 1000, "PLAYER", "PLAYING").invitable());
    }
}
