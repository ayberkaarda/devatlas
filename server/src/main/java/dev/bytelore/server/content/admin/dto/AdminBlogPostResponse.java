package dev.bytelore.server.content.admin.dto;

import dev.bytelore.server.domain.BlogSource;
import dev.bytelore.server.domain.BlogStatus;
import java.time.Instant;
import java.util.UUID;

/** A blog post as authoring sees it (§5.5.2): any status, canonical English columns. */
public record AdminBlogPostResponse(
    UUID id,
    String slug,
    String title,
    String bodyMarkdown,
    BlogStatus status,
    BlogSource source,
    String sourceUrl,
    UUID sourceUpdateId,
    Instant publishedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
