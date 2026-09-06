package dev.bytelore.server.auth;

import dev.bytelore.server.domain.Role;
import java.util.UUID;

/**
 * The verified contents of an access token.
 *
 * <p>Deliberately small. The token carries no email address and no other personally identifying
 * data: it travels on every request, is readable by anyone who holds it, and the only things the
 * server needs from it are who the caller is, what they may do, and an identifier to correlate logs
 * with.
 *
 * @param userId subject of the token
 * @param role the role the token was minted with
 * @param tokenId identifier used to correlate a request with the token that authorized it
 */
public record AccessTokenClaims(UUID userId, Role role, UUID tokenId) {}
