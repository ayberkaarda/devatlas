package dev.devatlas.server.translation.dto;

import java.time.Instant;

/**
 * {@code PUT /admin/translations/{entityType}/{entityId}/{locale}} response (§5.6): the stored row
 * plus the translated entity's new {@code content_version}. {@code contentVersion} is {@code null}
 * for {@code BLOG_POST}, which has no {@code content_version} column -- blog posts are not packaged
 * content (§5.4.1).
 */
public record TranslationWriteResponse(
    String locale,
    String title,
    String body,
    Instant updatedAt,
    long version,
    Integer contentVersion) {}
