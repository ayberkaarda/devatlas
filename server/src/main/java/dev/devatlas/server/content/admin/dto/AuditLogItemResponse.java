package dev.devatlas.server.content.admin.dto;

import dev.devatlas.server.domain.BlogStatus;
import dev.devatlas.server.domain.PipelineStep;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /admin/blog/posts/{id}/audit-log} (§5.7). {@code actorUserId} is {@code
 * null} for a machine step and non-null for every human decision -- that distinction is the audit
 * trail's whole purpose.
 */
public record AuditLogItemResponse(
    UUID id,
    PipelineStep step,
    UUID actorUserId,
    BlogStatus fromStatus,
    BlogStatus toStatus,
    String reason,
    Instant occurredAt) {}
