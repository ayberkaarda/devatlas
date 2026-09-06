package dev.bytelore.server.auth;

/**
 * The few things about a request that the authentication layer needs but that are not part of any
 * request body.
 *
 * <p>They exist for the audit trail rather than for any decision: a reuse-detection event that
 * cannot be tied back to an address and a request is an alert nobody can act on.
 *
 * @param clientIp remote address as the server saw it
 * @param requestId correlation identifier echoed from the request, or {@code null}
 */
public record ClientContext(String clientIp, String requestId) {}
