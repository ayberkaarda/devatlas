package dev.devatlas.server.content.dto;

import dev.devatlas.server.domain.Difficulty;
import java.time.Instant;
import java.util.UUID;

/** A lesson as it appears nested under a module in {@code GET /api/v1/tracks/{slug}} (§5.2.2). */
public record LessonSummaryResponse(
    UUID id,
    String slug,
    String title,
    Difficulty difficulty,
    Integer estimatedMinutes,
    int order,
    int contentVersion,
    String locale,
    String requestedLocale,
    boolean isFallback,
    Instant updatedAt) {}
