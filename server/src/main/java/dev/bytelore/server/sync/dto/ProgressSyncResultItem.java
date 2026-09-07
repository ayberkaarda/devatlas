package dev.bytelore.server.sync.dto;

import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.sync.ProgressSyncStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * What happened to one item of the batch.
 *
 * <p>A batch is never refused as a whole because of individual bad items. A desktop client can hold
 * weeks of offline writes, and one reference to a lesson an editor has since removed must not block
 * the other several hundred.
 *
 * @param lessonId the item this result is about
 * @param status APPLIED, STALE or REJECTED
 * @param code null unless {@code status} is REJECTED, in which case {@code LESSON_NOT_FOUND}
 * @param clamped true when the clock clamp rewrote this item's timestamps; the signal a client uses
 *     to surface a "your device clock looks wrong" hint, and worth logging, because a device that
 *     clamps on every sync has a real problem
 * @param serverClientUpdatedAt the value the server holds after processing, or null for a rejected
 *     item. Returning it is what lets a client that received STALE or {@code clamped} learn the
 *     winning timestamp without a second request, and write it back into its local row so the next
 *     sync compares against the same value the server holds
 */
public record ProgressSyncResultItem(
    UUID lessonId,
    ProgressSyncStatus status,
    ErrorCode code,
    boolean clamped,
    Instant serverClientUpdatedAt) {}
