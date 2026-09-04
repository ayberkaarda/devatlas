package dev.devatlas.server.content.admin.dto;

import dev.devatlas.server.domain.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminLessonResponse(
    UUID id,
    UUID moduleId,
    UUID trackId,
    String slug,
    String title,
    String bodyMarkdown,
    Difficulty difficulty,
    Integer estimatedMinutes,
    int order,
    int contentVersion,
    List<AdminCodeExampleResponse> codeExamples,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
