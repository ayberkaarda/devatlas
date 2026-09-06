package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/tracks/{trackId}/modules} (§5.4.3, §7.3). */
public record CreateModuleRequest(
    @NotBlank @Size(max = 200) String title,
    @Min(1) @Max(10000) Integer order,
    @Min(1) @Max(6000) Integer estimatedMinutes) {}
