package dev.bytelore.server.sync.dto;

import java.time.Instant;
import java.util.List;

/**
 * The result of one push batch.
 *
 * <p>{@code serverTime} is not decoration: it is how a client measures its own clock offset against
 * this server, which is the only way it can tell a clamp coming back to it apart from a bug.
 *
 * @param serverTime the instant the batch was processed at
 * @param appliedCount items stored, whether inserted or overwriting an older row
 * @param staleCount items discarded because the stored row is the same age or newer
 * @param rejectedCount items whose lesson did not resolve
 * @param clampedCount items whose timestamps the clock clamp rewrote
 * @param results one entry per submitted item, in submission order
 */
public record ProgressSyncResponse(
    Instant serverTime,
    int appliedCount,
    int staleCount,
    int rejectedCount,
    int clampedCount,
    List<ProgressSyncResultItem> results) {}
