package dev.bytelore.server.sync.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One stored progress row as the pull direction reports it.
 *
 * <p>Both timestamps travel, and they are not the same thing. {@code clientUpdatedAt} is what the
 * client compares on -- it applies the same strictly-greater rule locally, which is what makes
 * repeated syncs converge from either side of the wire. {@code updatedAt} is the server's own clock
 * and is only ever a paging cursor for {@code since}; comparing on it would let a row that merely
 * arrived later beat a change that actually happened later.
 *
 * @param lessonId the lesson this state is about
 * @param completedAt when it was completed, or null for an explicit un-completion
 * @param clientUpdatedAt the winning client timestamp the server holds
 * @param updatedAt when the server last wrote this row
 */
public record ProgressPullItem(
    UUID lessonId, Instant completedAt, Instant clientUpdatedAt, Instant updatedAt) {}
