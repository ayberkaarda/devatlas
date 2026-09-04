package dev.devatlas.server.content.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/tracks/{slug}/mindmap} and {@code GET /admin/tracks/{trackId}/mindmap}
 * (§5.2.4). {@code id} is the mind map's own identifier, distinct from {@code trackId}.
 */
public record MindMapResponse(
    UUID id,
    UUID trackId,
    int contentVersion,
    Instant updatedAt,
    String locale,
    String requestedLocale,
    boolean isFallback,
    MindMapNodeResponse root) {}
