package dev.bytelore.server.auth.dto;

import java.time.Instant;

/**
 * What a successful sign-in, registration or refresh returns.
 *
 * <p>{@code refreshToken} is null in cookie delivery mode -- the field is still present, and the
 * expiry is still returned in both modes, so a client can schedule its next refresh without ever
 * reading the token.
 *
 * @param accessToken the bearer token for authenticated calls
 * @param accessTokenExpiresAt when that token stops being accepted
 * @param refreshToken the new refresh token, or null when it was delivered as a cookie
 * @param refreshTokenExpiresAt when the refresh token expires if it is never used again
 * @param tokenType always {@code Bearer}
 * @param user the signed-in account
 */
public record AuthResponse(
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    String tokenType,
    UserSummaryResponse user) {}
