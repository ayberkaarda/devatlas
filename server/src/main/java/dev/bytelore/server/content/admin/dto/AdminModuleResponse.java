package dev.bytelore.server.content.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminModuleResponse(
    UUID id,
    UUID trackId,
    String title,
    int order,
    Integer estimatedMinutes,
    long lessonCount,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
