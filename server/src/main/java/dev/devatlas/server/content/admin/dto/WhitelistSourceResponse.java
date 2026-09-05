package dev.devatlas.server.content.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code /admin/whitelist-sources} (§5.7). */
public record WhitelistSourceResponse(
    UUID id,
    String name,
    String feedUrl,
    String verifyUrlPattern,
    boolean enabled,
    Instant lastFetchedAt,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
