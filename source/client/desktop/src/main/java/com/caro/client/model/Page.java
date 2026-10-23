package com.caro.client.model;

import java.util.List;

/** One page of a list. */
public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
