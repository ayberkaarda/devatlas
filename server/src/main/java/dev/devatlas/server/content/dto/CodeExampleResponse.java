package dev.devatlas.server.content.dto;

import java.util.UUID;

/** A code example as it appears nested inside {@code GET /api/v1/lessons/{slug}} (§5.2.3). */
public record CodeExampleResponse(
    UUID id, String language, String code, String caption, int order) {}
