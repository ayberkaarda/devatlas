package dev.bytelore.server.sync.dto;

import dev.bytelore.server.common.PageResponse;
import java.time.Instant;
import java.util.List;

/**
 * A page of stored progress, plus the server's clock.
 *
 * <p>Field for field the standard collection envelope with one addition, {@code serverTime}, which
 * the contract specifies for this endpoint alone. It is carried here rather than added to {@link
 * PageResponse} because it is meaningful only where a client is reconciling clocks: putting it on
 * every collection endpoint would make it look like part of the paging contract, and a field that
 * appears everywhere but means something in one place is a field that gets read wrong. The page
 * arithmetic still comes from {@link PageResponse#of} so the two envelopes cannot drift.
 *
 * @param items the page's rows, in the resolved sort order
 * @param page zero-based page index that was served
 * @param size page size that was served
 * @param totalElements total row count across every page
 * @param totalPages total page count
 * @param serverTime the instant the page was read at, so the client can measure its clock offset
 */
public record ProgressPullResponse(
    List<ProgressPullItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    Instant serverTime) {

  public static ProgressPullResponse of(PageResponse<ProgressPullItem> page, Instant serverTime) {
    return new ProgressPullResponse(
        page.items(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages(),
        serverTime);
  }
}
