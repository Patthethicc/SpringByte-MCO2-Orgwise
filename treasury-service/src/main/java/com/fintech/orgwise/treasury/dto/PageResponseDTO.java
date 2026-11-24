package com.fintech.orgwise.treasury.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable JSON shape for paginated results (Spring's {@code PageImpl} JSON is not a stable API).
 *
 * @param content       items on this page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of items
 * @param totalPages    total number of pages
 * @param <T>           item type
 */
public record PageResponseDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Converts a Spring Data page, mapping each element.
     *
     * @param page   source page
     * @param mapper element mapper
     * @param <E>    source element type
     * @param <T>    target element type
     * @return the converted page
     */
    public static <E, T> PageResponseDTO<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponseDTO<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
