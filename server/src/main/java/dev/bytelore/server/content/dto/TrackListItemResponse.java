package dev.bytelore.server.content.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /api/v1/tracks} (§5.2.1). */
public record TrackListItemResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String icon,
    int order,
    String locale,
    String requestedLocale,
    boolean isFallback,
    long moduleCount,
    long lessonCount,
    int contentVersion,
    Instant updatedAt) {}
