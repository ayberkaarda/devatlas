package dev.devatlas.server.content.admin.dto;

import dev.devatlas.server.domain.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code PATCH /admin/lessons/{id}} (§5.4.4): the create fields, all optional, plus a move. */
public record UpdateLessonRequest(
    @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 200) String title,
    @Size(max = 200000) String bodyMarkdown,
    Difficulty difficulty,
    @Min(1) @Max(600) Integer estimatedMinutes,
    @Min(1) @Max(10000) Integer order,
    UUID moduleId,
    @NotNull Long version) {}
