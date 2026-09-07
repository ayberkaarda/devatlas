package dev.bytelore.server.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A batch of local completion state, uploaded in one transaction.
 *
 * <p>The documented ceiling of 500 items is <strong>not</strong> expressed as a {@code @Size(max =
 * 500)} here on purpose. Bean Validation failures are reported as {@code VALIDATION_FAILED} with
 * status 400, and an oversized batch has its own code and its own status -- {@code
 * SYNC_BATCH_TOO_LARGE}, 413. Annotating the maximum would silently replace both with the generic
 * pair. The lower bound has no such conflict, so it stays here where a client gets a field-level
 * message for it.
 *
 * @param items 1..500 lesson states, each for a distinct lesson
 */
public record ProgressSyncRequest(@NotEmpty List<@NotNull @Valid ProgressSyncItem> items) {}
