package dev.bytelore.server.content.dto;

import java.time.Instant;

/**
 * The caller's progress on a lesson, nested inside {@code GET /api/v1/lessons/{slug}} (§5.2.3). The
 * whole object is {@code null} for an anonymous caller; when present, {@code completedAt} is itself
 * nullable and means "not yet completed" rather than "unknown".
 */
public record ProgressResponse(Instant completedAt) {}
