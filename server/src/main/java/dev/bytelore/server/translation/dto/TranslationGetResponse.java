package dev.bytelore.server.translation.dto;

import dev.bytelore.server.domain.TranslationEntityType;
import java.util.List;
import java.util.UUID;

/** {@code GET /admin/translations/{entityType}/{entityId}} (§5.6). */
public record TranslationGetResponse(
    TranslationEntityType entityType,
    UUID entityId,
    TranslationCanonical canonical,
    List<TranslationItem> translations,
    List<String> missingLocales) {}
