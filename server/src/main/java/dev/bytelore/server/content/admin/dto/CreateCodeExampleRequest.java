package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/lessons/{lessonId}/code-examples} (§5.4.5, §7.5). */
public record CreateCodeExampleRequest(
    @NotBlank String language,
    @NotBlank @Size(max = 20000) String code,
    @Size(max = 300) String caption,
    @Min(1) @Max(10000) Integer order) {}
