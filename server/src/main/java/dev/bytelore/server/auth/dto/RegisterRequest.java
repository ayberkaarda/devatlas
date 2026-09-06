package dev.bytelore.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration. Creates an ordinary account and signs it in; there is no separate confirmation
 * step.
 *
 * <p>{@code role} is deliberately absent. Role assignment is never accepted from a client, so there
 * is no field here for a caller to set and no branch anywhere that reads one.
 *
 * @param email case-insensitive, stored lowercased
 * @param password length is the only requirement -- composition rules push people towards
 *     predictable substitutions and shorter secrets
 * @param locale optional interface locale, defaults to English
 * @param theme optional appearance preference, defaults to following the system
 * @param deviceLabel optional label for the refresh token, so a person can tell their devices apart
 * @param tokenDelivery where the refresh token should be returned; accepted only here and on login
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password,
    @Pattern(regexp = "^(en|tr|fr|de)$") String locale,
    @Pattern(regexp = "^(LIGHT|DARK|SYSTEM)$") String theme,
    @Size(max = 64) String deviceLabel,
    @Pattern(regexp = "^(COOKIE|BODY)$") String tokenDelivery) {}
