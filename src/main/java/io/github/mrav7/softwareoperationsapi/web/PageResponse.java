package io.github.mrav7.softwareoperationsapi.web;

import java.util.List;

import org.springframework.data.domain.Page;

/** Stable HTTP representation for paginated collection responses. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static <T> PageResponse<T> from(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
