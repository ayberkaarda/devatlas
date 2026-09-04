package dev.devatlas.server.common;

import java.util.List;

/**
 * The fixed envelope every collection endpoint returns.
 *
 * <p>{@code items} is never {@code null}; an empty page is {@code []} with {@code 200}, not a
 * missing field. Offset paging is used throughout rather than a cursor: the admin lists need a
 * total count for their UI, and the data sets this API serves are hundreds of rows, not millions.
 *
 * @param items the page's rows, in the resolved sort order
 * @param page zero-based page index that was served
 * @param size page size that was served
 * @param totalElements total row count across every page
 * @param totalPages total page count, derived from {@code totalElements} and {@code size}
 */
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
    int totalPages = size == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
    return new PageResponse<>(items, page, size, totalElements, totalPages);
  }
}
