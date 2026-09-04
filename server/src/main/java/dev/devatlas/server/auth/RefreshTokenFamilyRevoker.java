package dev.devatlas.server.auth;

import dev.devatlas.server.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits a family-wide refresh-token revocation in its own transaction, independent of whatever
 * happens in the caller after it returns.
 *
 * <p>Reuse detection revokes a family and then reports the event by throwing {@link
 * dev.devatlas.server.common.ApiException}. An unchecked exception escaping a
 * {@code @Transactional} method rolls that transaction back by default -- which, if the revoke ran
 * in the same transaction as the throw, would silently undo the very revocation the exception is
 * reporting. {@code REQUIRES_NEW} suspends the caller's transaction, runs the revoke in a separate
 * one, and commits it before control returns, so the caller's later rollback -- whether it throws
 * or not -- can no longer touch it.
 *
 * <p>This has to be a separate bean rather than a second method on {@link RefreshTokenService}.
 * {@code @Transactional} is implemented as a proxy wrapped around the bean; a call from one method
 * to another on {@code this} inside the same class never goes through that proxy; it is a direct
 * JVM call, so {@code REQUIRES_NEW} on such a method is silently ignored -- the revoke would still
 * run inside the original transaction, and the bug this class exists to fix would remain, now
 * wearing an annotation that looks like a fix. Calling through an injected reference to a distinct
 * bean cannot fall into that trap: the reference Spring injects always is the proxy.
 */
@Service
public class RefreshTokenFamilyRevoker {

  private final RefreshTokenRepository repository;

  public RefreshTokenFamilyRevoker(RefreshTokenRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyInNewTransaction(UUID familyId, Instant revokedAt, String reason) {
    repository.revokeFamily(familyId, revokedAt, reason);
  }
}
