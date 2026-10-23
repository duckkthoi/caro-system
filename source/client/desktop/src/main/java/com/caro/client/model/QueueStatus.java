package com.caro.client.model;

/** Quick-match state. ratingRange null while inQueue means "any opponent". */
public record QueueStatus(boolean inQueue, Long since, Integer ratingRange, int queueSize) {
}
