package dev.devatlas.server.content.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * One node of a submitted mind map tree (§5.2.4, §7.6). Structural rules that cannot be expressed
 * as a field constraint -- depth, total node count, id uniqueness within the map, and a {@code
 * lessonId} belonging to the same track -- are validated in the service and reported as {@code 422
 * MIND_MAP_INVALID}, not {@code VALIDATION_FAILED}.
 */
public record MindMapNodeRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$") String id,
    @NotBlank @Size(min = 1, max = 120) String label,
    UUID lessonId,
    @NotNull List<@Valid MindMapNodeRequest> children) {}
