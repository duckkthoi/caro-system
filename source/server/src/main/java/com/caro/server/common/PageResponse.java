package com.caro.server.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** One page of a list returned by the REST API and the socket protocol (docs/protocol.md, section 5). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }

    /** For when the items were mapped in bulk (e.g. after batch-loading related rows). */
    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    /** Validates the page parameters sent by a client. */
    public static Pageable request(int page, int size, Sort sort) {
        if (page < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "page phải >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "size phải từ 1 đến " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
