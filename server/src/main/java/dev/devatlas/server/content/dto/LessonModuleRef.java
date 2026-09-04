package dev.devatlas.server.content.dto;

import java.util.UUID;

/** The owning module's identity, as embedded inside {@code GET /api/v1/lessons/{slug}} (§5.2.3). */
public record LessonModuleRef(UUID id, String title, int order) {}
