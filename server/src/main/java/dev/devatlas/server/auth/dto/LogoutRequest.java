package dev.devatlas.server.auth.dto;

/**
 * Sign-out. Like refresh, the credential may arrive in the body or the cookie.
 *
 * @param refreshToken the token to revoke, when it is not in a cookie
 */
public record LogoutRequest(String refreshToken) {}
