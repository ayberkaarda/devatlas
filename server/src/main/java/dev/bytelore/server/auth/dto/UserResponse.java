package dev.bytelore.server.auth.dto;

import dev.bytelore.server.domain.Role;
import dev.bytelore.server.domain.Theme;
import dev.bytelore.server.domain.UserLocale;
import java.time.Instant;
import java.util.UUID;

/**
 * The account as its owner reads it.
 *
 * <p>No version field: preferences resolve last-write-wins, so there is nothing for a client to
 * echo back.
 */
public record UserResponse(
    UUID id,
    String email,
    Role role,
    UserLocale locale,
    Theme theme,
    Instant createdAt,
    Instant updatedAt) {}
