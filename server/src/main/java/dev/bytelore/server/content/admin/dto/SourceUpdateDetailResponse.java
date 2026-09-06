package dev.bytelore.server.content.admin.dto;

import dev.bytelore.server.domain.VerifyStatus;
import dev.bytelore.server.pipeline.VerifyCheckRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /admin/source-updates/{id}} (§5.7), and the {@code source_update} field nested inside
 * {@code GET /admin/review-queue/{postId}}. {@code verifyChecks} is ordered as executed; the first
 * failing entry is the reason a {@code REJECTED} update never became a draft.
 */
public record SourceUpdateDetailResponse(
    UUID id,
    WhitelistSourceSummary whitelistSource,
    String versionString,
    String contentHash,
    Instant fetchedAt,
    VerifyStatus verifyStatus,
    List<VerifyCheckRecord> verifyChecks,
    String rawContent) {}
