package dev.bytelore.server.pipeline;

import java.util.List;
import java.util.UUID;

/**
 * The result of one fetch cycle against one whitelist source -- the shape {@code POST
 * /admin/whitelist-sources/{id}/fetch} returns (§5.7), also produced (and only logged, not
 * returned) by the scheduled run.
 *
 * <p>{@code fetched = created + duplicates + rejected} always. An item deferred by a transient
 * upstream failure (currently: a {@code 403}/{@code 429} from the version-confirmation request) is
 * not counted anywhere in this record -- it was skipped, not decided, and is retried in full on the
 * next cycle. Its only trace is a {@code PipelineAuditLog} row; see §5.7 of the REST contract.
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
