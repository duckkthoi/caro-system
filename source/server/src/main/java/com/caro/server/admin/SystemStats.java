package com.caro.server.admin;

/** Numbers for the admin dashboard. The first two come from the database, the rest from server memory. */
public record SystemStats(
        long totalUsers,
        long lockedUsers,
        long totalMatches,
        long matchesLast24h,
        int onlineUsers,
        int rooms,
        int activeMatches) {
}
