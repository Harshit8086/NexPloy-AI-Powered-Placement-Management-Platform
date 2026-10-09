package com.nexploy.campuslink.shared.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from raw {@code ?page=&size=&sort=} query params with safe defaults and a
 * hard size cap. Pagination is <b>opt-in</b>: list endpoints only switch to the {@link PageResponse}
 * envelope when at least one of {@code page}/{@code size} is present, so existing array-returning
 * callers keep working. This helper is therefore invoked only once a paged response has been chosen.
 *
 * <p>Defaults: page 0, size 20, capped at 100. {@code sort} is {@code "property"} or
 * {@code "property,(asc|desc)"}; blank/absent means unsorted (repository/DB default order).
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /** True when the caller requested pagination (either param supplied). */
    public static boolean requested(Integer page, Integer size) {
        return page != null || size != null;
    }

    public static Pageable of(Integer page, Integer size, String sort) {
        int resolvedPage = (page == null || page < 0) ? 0 : page;
        int resolvedSize = (size == null) ? DEFAULT_SIZE : size;
        if (resolvedSize < 1) {
            resolvedSize = DEFAULT_SIZE;
        }
        if (resolvedSize > MAX_SIZE) {
            resolvedSize = MAX_SIZE;
        }
        return PageRequest.of(resolvedPage, resolvedSize, parseSort(sort));
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (property.isEmpty()) {
            return Sort.unsorted();
        }
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
