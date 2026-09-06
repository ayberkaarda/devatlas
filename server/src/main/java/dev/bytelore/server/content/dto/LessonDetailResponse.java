package dev.bytelore.server.content.dto;

import dev.bytelore.server.domain.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/lessons/{slug}} (§5.2.3). {@code codeExamples} is never {@code null}, it may
 * be empty; {@code progress} is {@code null} for an anonymous caller and for an authenticated one
 * who has not completed the lesson.
 */
public record LessonDetailResponse(
    UUID id,
    String slug,
    String title,
    String bodyMarkdown,
    Difficulty difficulty,
    Integer estimatedMinutes,
    int order,
    int contentVersion,
    String locale,
    String requestedLocale,
    boolean isFallback,
    Instant updatedAt,
    LessonModuleRef module,
    LessonTrackRef track,
    List<CodeExampleResponse> codeExamples,
    ProgressResponse progress) {}
