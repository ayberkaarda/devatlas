package dev.bytelore.server.content.dto;

import dev.bytelore.server.domain.BlogSource;
import java.time.Instant;
import java.util.UUID;

/** {@code GET /api/v1/blog/posts/{slug}} (§5.2.6). */
public record BlogPostDetailResponse(
    UUID id,
    String slug,
    String title,
    String bodyMarkdown,
    BlogSource source,
    String sourceUrl,
    Instant publishedAt,
    Instant updatedAt,
    String locale,
    String requestedLocale,
    boolean isFallback) {}
