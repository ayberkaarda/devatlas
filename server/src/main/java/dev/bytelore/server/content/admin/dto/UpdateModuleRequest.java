package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code PATCH /admin/modules/{id}} (§5.4.3). {@code trackId} moves the module to another track;
 * every lesson beneath it moves too and each gets its own {@code content_version} bump (§5.4.1).
 */
public record UpdateModuleRequest(
    UUID trackId,
    @Size(max = 200) String title,
    @Min(1) @Max(10000) Integer order,
    @Min(1) @Max(6000) Integer estimatedMinutes,
    @NotNull Long version) {}
