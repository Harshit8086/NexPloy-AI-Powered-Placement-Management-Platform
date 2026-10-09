package com.nexploy.campuslink.shared.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Stable pagination envelope returned by list endpoints. Deliberately does not leak Spring
 * Data's {@link Page} shape (which carries an unstable {@code pageable}/{@code sort} object
 * graph) to the frontend; only the fields the UI needs are exposed, all camelCase.
 *
 * @param content       the page of mapped response DTOs
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total rows across all pages
 * @param totalPages    total number of pages
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    /** Maps a Spring Data {@link Page} of entities into a {@code PageResponse} of DTOs. */
    public static <E, T> PageResponse<T> of(Page<E> source, java.util.function.Function<E, T> mapper) {
        return new PageResponse<>(
            source.getContent().stream().map(mapper).toList(),
            source.getNumber(),
            source.getSize(),
            source.getTotalElements(),
            source.getTotalPages()
        );
    }

    /**
     * Slices an already-materialized list into a {@code PageResponse}. Used where the source
     * cannot be paged at the database level (e.g. an in-memory, campus-filtered result set).
     */
    public static <T> PageResponse<T> ofList(List<T> all, Pageable pageable) {
        int total = all.size();
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<T> content = List.copyOf(all.subList(start, end));
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }
}
