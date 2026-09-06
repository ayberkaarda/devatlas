package dev.bytelore.server.content.admin.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /admin/whitelist-sources/{id}/fetch} (§5.7). {@code fetched = created + duplicates +
 * rejected} always.
 */
public record WhitelistSourceFetchResponse(
    UUID whitelistSourceId,
    int fetched,
    int created,
    int duplicates,
    int rejected,
    List<UUID> createdSourceUpdateIds,
    List<RejectionItem> rejections,
    long durationMs) {}
