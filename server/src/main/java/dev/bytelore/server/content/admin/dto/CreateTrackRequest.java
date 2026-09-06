package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/tracks} (§5.4.2, §7.2). */
public record CreateTrackRequest(
    @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 2000) String description,
    @Size(max = 64) @Pattern(regexp = "^[a-z0-9-]+$") String icon,
    @Min(1) @Max(10000) Integer order,
    Boolean published) {}
