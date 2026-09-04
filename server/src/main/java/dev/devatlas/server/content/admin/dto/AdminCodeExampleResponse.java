package dev.devatlas.server.content.admin.dto;

import java.util.UUID;

/**
 * A code example as authoring sees it. {@code lessonContentVersion} is the owning lesson's current
 * {@code content_version} -- on a create/update/delete response it is the version *after* the bump
 * that write caused (§5.4.5).
 */
public record AdminCodeExampleResponse(
    UUID id,
    UUID lessonId,
    String language,
    String code,
    String caption,
    int order,
    int lessonContentVersion,
    long version) {}
