package com.caro.server.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The IP address a request really comes from (docs/rules.md, section 13). Behind nginx every request comes from the
 * proxy, which passes the browser's address in {@code X-Real-IP}. That header is only trusted when the direct peer is
 * on a loopback or private network (i.e. it is our proxy); anyone else could simply make it up.
 */
public final class ClientIp {

    public static final String HEADER = "X-Real-IP";

    private ClientIp() {
    }

    /** @param realIpHeader the X-Real-IP header, may be null */
    public static String resolve(InetAddress peer, String realIpHeader) {
        if (peer == null) {
            return "unknown";
        }
        boolean fromProxy = peer.isLoopbackAddress() || peer.isSiteLocalAddress();
        if (fromProxy && realIpHeader != null && !realIpHeader.isBlank()) {
            return realIpHeader.trim();
        }
        return peer.getHostAddress();
    }

    public static String resolve(String peerAddress, String realIpHeader) {
        try {
            return resolve(InetAddress.getByName(peerAddress), realIpHeader); // a literal IP: no DNS lookup
        } catch (UnknownHostException e) {
            return peerAddress;
        }
    }
}
