package dev.bytelore.server.content.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** A track as authoring sees it: canonical English columns, never a translation fallback. */
public record AdminTrackResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String icon,
    int order,
    boolean published,
    int contentVersion,
    long moduleCount,
    long lessonCount,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
