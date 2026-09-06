package dev.bytelore.server.content.admin.dto;

import dev.bytelore.server.domain.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/modules/{moduleId}/lessons} (§5.4.4, §7.4). */
public record CreateLessonRequest(
    @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 200000) String bodyMarkdown,
    @NotNull Difficulty difficulty,
    @Min(1) @Max(600) Integer estimatedMinutes,
    @Min(1) @Max(10000) Integer order) {}
