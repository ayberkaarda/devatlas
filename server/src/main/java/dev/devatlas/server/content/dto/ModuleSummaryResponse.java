package dev.devatlas.server.content.dto;

import java.util.List;
import java.util.UUID;

/** A module as it appears nested in {@code GET /api/v1/tracks/{slug}} (§5.2.2). */
public record ModuleSummaryResponse(
    UUID id,
    String title,
    int order,
    Integer estimatedMinutes,
    String locale,
    String requestedLocale,
    boolean isFallback,
    List<LessonSummaryResponse> lessons) {}
