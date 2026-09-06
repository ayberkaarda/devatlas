package dev.bytelore.server.content.dto;

import dev.bytelore.server.domain.BlogSource;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /api/v1/blog/posts} (§5.2.5). {@code excerpt} is derived server-side. */
public record BlogPostListItemResponse(
    UUID id,
    String slug,
    String title,
    String excerpt,
    BlogSource source,
    String sourceUrl,
    Instant publishedAt,
    String locale,
    String requestedLocale,
    boolean isFallback) {}
