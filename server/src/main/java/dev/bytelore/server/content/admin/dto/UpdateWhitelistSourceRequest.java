package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /admin/whitelist-sources/{id}} (§5.7). Every field is optional -- {@code null} means
 * "leave unchanged" -- except {@code version}. Disabling a source ({@code {"enabled": false,
 * "version": n}}) is how a source with existing {@code SourceUpdate} rows is retired, since
 * deleting one is refused with {@code 409 PARENT_NOT_EMPTY}.
 */
public record UpdateWhitelistSourceRequest(
    @Size(max = 120) String name,
    @Size(max = 2000) String feedUrl,
    @Size(max = 2000) String verifyUrlPattern,
    Boolean enabled,
    @NotNull Long version) {}
