package dev.bytelore.server.content.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /api/v1/tracks/{slug}} (§5.2.2). Lesson bodies are not included here. */
public record TrackDetailResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String icon,
    int order,
    String locale,
    String requestedLocale,
    boolean isFallback,
    int contentVersion,
    boolean hasMindMap,
    Instant updatedAt,
    List<ModuleSummaryResponse> modules) {}
