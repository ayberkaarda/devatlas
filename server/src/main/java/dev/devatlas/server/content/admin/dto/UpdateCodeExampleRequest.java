package dev.devatlas.server.content.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /admin/code-examples/{id}} (§5.4.5). */
public record UpdateCodeExampleRequest(
    String language,
    @Size(max = 20000) String code,
    @Size(max = 300) String caption,
    @Min(1) @Max(10000) Integer order,
    @NotNull Long version) {}
