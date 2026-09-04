package dev.devatlas.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /admin/tracks/{id}} (§5.4.2). Every content field is optional -- {@code null} means
 * "leave unchanged" -- except {@code version}, which is mandatory (§2.6 optimistic concurrency).
 */
public record UpdateTrackRequest(
    @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 200) String title,
    @Size(max = 2000) String description,
    @Size(max = 64) @Pattern(regexp = "^[a-z0-9-]+$") String icon,
    @Min(1) @Max(10000) Integer order,
    Boolean published,
    @NotNull Long version) {}
