package com.caro.server.lobby;

/**
 * Quick-match state of one player (QUICK_MATCH reply, QUEUE_STATUS event).
 * ratingRange is the accepted rating gap; null while in the queue means "any opponent".
 */
public record QueueStatus(boolean inQueue, Long since, Integer ratingRange, int queueSize) {

    static QueueStatus notQueued(int queueSize) {
        return new QueueStatus(false, null, null, queueSize);
    }
}
