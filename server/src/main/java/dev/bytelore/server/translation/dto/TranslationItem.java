package dev.bytelore.server.translation.dto;

import java.time.Instant;

/** One stored translation row, nested inside {@code GET /admin/translations/{type}/{id}} (§5.6). */
public record TranslationItem(
    String locale, String title, String body, Instant updatedAt, long version) {}
