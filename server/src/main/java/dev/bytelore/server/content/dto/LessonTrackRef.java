package dev.bytelore.server.content.dto;

import java.util.UUID;

/** The owning track's identity, as embedded inside {@code GET /api/v1/lessons/{slug}} (§5.2.3). */
public record LessonTrackRef(UUID id, String slug, String title) {}
