package dev.devatlas.server.translation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /admin/translations/{entityType}/{entityId}/{locale}} (§5.6, §7.7). {@code title} is
 * always required; whether {@code body} is required depends on {@code entityType} and is checked in
 * the service, not here -- it is a class-level rule this DTO alone cannot express.
 */
public record TranslationUpsertRequest(
    @NotBlank @Size(max = 200) String title, @Size(max = 200000) String body, Long version) {}
