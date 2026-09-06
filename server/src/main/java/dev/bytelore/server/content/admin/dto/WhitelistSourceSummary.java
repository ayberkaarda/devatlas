package dev.bytelore.server.content.admin.dto;

import java.util.UUID;

/** The whitelist source fields the review screen needs alongside a source update (§5.7). */
public record WhitelistSourceSummary(UUID id, String name, String feedUrl) {}
