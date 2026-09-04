package dev.devatlas.server.auth.dto;

import dev.devatlas.server.domain.Role;
import dev.devatlas.server.domain.Theme;
import dev.devatlas.server.domain.UserLocale;
import java.time.Instant;
import java.util.UUID;

/** The account as it appears inside a sign-in response. */
public record UserSummaryResponse(
    UUID id, String email, Role role, UserLocale locale, Theme theme, Instant createdAt) {}
