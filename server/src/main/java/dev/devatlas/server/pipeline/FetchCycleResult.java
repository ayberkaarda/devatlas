package dev.devatlas.server.pipeline;

import java.util.List;
import java.util.UUID;

/**
 * The result of one fetch cycle against one whitelist source -- the shape {@code POST
 * /admin/whitelist-sources/{id}/fetch} returns (§5.7), also produced (and only logged, not
 * returned) by the scheduled run.
 *
 * <p>{@code fetched = created + duplicates + rejected} always.
 */
public record FetchCycleResult(
    UUID whitelistSourceId,
    int fetched,
    int created,
    int duplicates,
    int rejected,
    List<UUID> createdSourceUpdateIds,
    List<Rejection> rejections,
    long durationMs) {}
