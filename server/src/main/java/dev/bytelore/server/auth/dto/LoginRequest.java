package dev.bytelore.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-in.
 *
 * <p>An unknown email and a wrong password produce the same code, the same message and comparable
 * timing. Anything else turns this endpoint into a way to enumerate who has an account.
 *
 * @param email case-insensitive
 * @param password the presented secret
 * @param deviceLabel optional label recorded on the refresh token
 * @param tokenDelivery where the refresh token should be returned; accepted only here and on
 *     registration
 */
public record LoginRequest(
    @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(max = 128) String password,
    @Size(max = 64) String deviceLabel,
    @Pattern(regexp = "^(COOKIE|BODY)$") String tokenDelivery) {}
