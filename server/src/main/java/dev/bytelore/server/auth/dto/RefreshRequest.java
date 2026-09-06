package dev.bytelore.server.auth.dto;

/**
 * Refresh. The token may arrive here or in the cookie, and the response answers on whichever
 * channel it came in on.
 *
 * <p>There is no {@code token_delivery} field, and its absence is load-bearing. If this endpoint
 * honoured a client-chosen delivery mode, script injected into a browser build could ask for the
 * value in the body; the browser would attach the HttpOnly cookie automatically and the server
 * would hand the plaintext back to JavaScript -- converting an HttpOnly cookie into a readable one
 * on request, and a transient injection into a long-lived session theft.
 *
 * @param refreshToken the token, when the caller keeps it itself rather than in a cookie
 */
public record RefreshRequest(String refreshToken) {}
