package dev.devatlas.server.content.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /admin/whitelist-sources} (§5.7, §7.9). Both URLs must be absolute {@code https://}
 * and {@code verifyUrlPattern} must contain exactly one {@code {version}} placeholder -- checked in
 * the service, since neither rule is expressible as a single field annotation without duplicating
 * the same regex twice for two different error codes.
 */
public record CreateWhitelistSourceRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 2000) String feedUrl,
    @NotBlank @Size(max = 2000) String verifyUrlPattern,
    Boolean enabled) {}
