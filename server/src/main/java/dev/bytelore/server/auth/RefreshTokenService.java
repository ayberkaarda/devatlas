package dev.bytelore.server.auth;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.config.AuthProperties;
import dev.bytelore.server.domain.RefreshToken;
import dev.bytelore.server.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The refresh-token state machine: issue, rotate, detect replay, revoke.
 *
 * <p>Refresh tokens are opaque 256-bit random values rather than self-contained JWTs, for one
 * reason: they must be revocable. A stolen JWT refresh token cannot be invalidated before it
 * expires, and "wait sixty days" is not an incident response.
 *
 * <p>Only the SHA-256 digest of a token is stored. A plain digest is the right primitive here even
 * though a password would need a slow hash: the value is 256 bits of uniform randomness, so there
 * is no dictionary to run against it, and the lookup happens on every refresh.
 */
@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private static final int TOKEN_BYTES = 32;

  /** Recorded on the token row so an operator can tell an incident from an ordinary sign-out. */
  static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";

  static final String REASON_LOGOUT = "LOGOUT";
  static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
  static final String REASON_GRACE_SUPERSEDED = "GRACE_SUPERSEDED";

  private final RefreshTokenRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final RefreshTokenFamilyRevoker familyRevoker;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository repository,
      AuthProperties properties,
      Clock clock,
      RefreshTokenFamilyRevoker familyRevoker) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.familyRevoker = familyRevoker;
  }

  /**
   * A token as the client will see it, paired with the row that describes it. The plaintext exists
   * only for the duration of the response that carries it.
   */
  public record IssuedRefreshToken(String token, RefreshToken stored) {}

  /** Outcome of a successful exchange. */
  public record RotationResult(UUID userId, IssuedRefreshToken refresh) {}

  /**
   * Issues a token, starting a new family when {@code familyId} is null.
   *
   * <p>Every issue -- first sign-in, ordinary rotation, or a grace re-mint -- gets a full fresh
   * lifetime. The lifetime is per token, not per family, so regular use extends a session and only
   * absence ends it.
   */
  @Transactional
  public IssuedRefreshToken issue(
      UUID userId, UUID familyId, String deviceLabel, boolean mintedByGrace) {
    byte[] raw = new byte[TOKEN_BYTES];
    random.nextBytes(raw);
    String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    RefreshToken token = new RefreshToken();
    token.setId(UuidV7.randomUuid());
    token.setUserId(userId);
    token.setFamilyId(familyId != null ? familyId : UuidV7.randomUuid());
    token.setTokenDigest(digest(plaintext));
    token.setDeviceLabel(deviceLabel);
    token.setIssuedAt(now);
    token.setExpiresAt(now.plus(properties.getRefreshTtl()));
    token.setMintedByGrace(mintedByGrace);
    token.setCreatedAt(now);
    return new IssuedRefreshToken(plaintext, repository.save(token));
  }

  /**
   * Exchanges a presented refresh token for a successor.
   *
   * <p>The order of the checks is the design. A replayed token is examined before an expired one,
   * because a replay is a security event that must close the family, and an expiry is not.
   *
   * @throws ApiException with {@code REFRESH_TOKEN_INVALID}, {@code REFRESH_TOKEN_EXPIRED} or
   *     {@code REFRESH_TOKEN_REUSED}
   */
  @Transactional
  public RotationResult rotate(String presented, ClientContext context) {
    RefreshToken token =
        repository
            .findByTokenDigest(digest(presented))
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token is not recognised."));

    if (token.getRevokedAt() != null) {
      throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has been revoked.");
    }

    if (token.getRotatedAt() != null) {
      return handleAlreadyRotated(token, context);
    }

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    if (!token.getExpiresAt().isAfter(now)) {
      throw new ApiException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh token has expired.");
    }

    IssuedRefreshToken successor =
        issue(token.getUserId(), token.getFamilyId(), token.getDeviceLabel(), false);
    token.setRotatedAt(now);
    token.setSuccessorId(successor.stored().getId());
    repository.save(token);
    return new RotationResult(token.getUserId(), successor);
  }

  /**
   * A token presented after it was already exchanged. Either the client never received the response
   * to that exchange, or someone is replaying a stolen value; the two are indistinguishable from
   * the request alone, so they are separated by time and by whether the chain has moved on.
   */
  private RotationResult handleAlreadyRotated(RefreshToken token, ClientContext context) {
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    boolean withinGrace = token.getRotatedAt().plus(properties.getRefreshGrace()).isAfter(now);

    Optional<RefreshToken> successor =
        Optional.ofNullable(token.getSuccessorId()).flatMap(repository::findById);
    boolean successorUntouched =
        successor.filter(s -> s.getRotatedAt() == null && s.getRevokedAt() == null).isPresent();

    if (withinGrace && successorUntouched) {
      // The successor is revoked in the same transaction, so this path never leaves two live tokens
      // in one family. If the lost response did reach a second consumer after all, that consumer's
      // token is now dead and its next call trips reuse detection -- which is the correct outcome.
      //
      // Returning the successor again is the formulation that first suggests itself, and it is not
      // implementable: only a digest of it was ever stored, so the plaintext issued moments ago
      // cannot be reproduced. Re-minting is the only way to answer the request at all.
      RefreshToken staleSuccessor = successor.orElseThrow();
      staleSuccessor.setRevokedAt(now);
      staleSuccessor.setRevokedReason(REASON_GRACE_SUPERSEDED);
      repository.save(staleSuccessor);

      IssuedRefreshToken reminted =
          issue(token.getUserId(), token.getFamilyId(), token.getDeviceLabel(), true);
      token.setSuccessorId(reminted.stored().getId());
      repository.save(token);

      int graceEvents = repository.countByFamilyIdAndMintedByGraceTrue(token.getFamilyId());
      log.info(
          "Refresh grace re-mint: user={} family={} graceEvents={} ip={} requestId={}",
          token.getUserId(),
          token.getFamilyId(),
          graceEvents,
          context.clientIp(),
          context.requestId());
      return new RotationResult(token.getUserId(), reminted);
    }

    // Either the window has passed, or someone else already advanced the chain. Both mean the
    // legitimate client and whoever else holds this value cannot both be the rightful holder, and
    // the only safe answer is to end every session descended from that sign-in.
    //
    // Committed through familyRevoker in a transaction of its own: the ApiException thrown a few
    // lines below propagates out of this @Transactional method, which rolls the current transaction
    // back by default. If the revoke ran inside that same transaction, the rollback would silently
    // undo it -- the response would still say REFRESH_TOKEN_REUSED, but nothing would actually be
    // revoked. See RefreshTokenFamilyRevoker's Javadoc for why this has to be a distinct bean.
    familyRevoker.revokeFamilyInNewTransaction(token.getFamilyId(), now, REASON_REUSE_DETECTED);
    log.warn(
        "Refresh token reuse detected; family revoked: user={} family={} ip={} requestId={}",
        token.getUserId(),
        token.getFamilyId(),
        context.clientIp(),
        context.requestId());
    throw new ApiException(
        ErrorCode.REFRESH_TOKEN_REUSED,
        "Refresh token was already used; the token family has been revoked.");
  }

  /**
   * Revokes the family a presented token belongs to, or every family of its owner.
   *
   * <p>Silent about the outcome by design: sign-out is unauthenticated and must not become an
   * oracle that tells a caller whether a token they hold is still good.
   */
  @Transactional
  public void revokeByPresentedToken(String presented, boolean allDevices) {
    Optional<RefreshToken> found = repository.findByTokenDigest(digest(presented));
    if (found.isEmpty()) {
      return;
    }
    RefreshToken token = found.get();
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    if (allDevices) {
      repository.revokeAllForUserExceptFamily(token.getUserId(), null, now, REASON_LOGOUT);
    } else {
      repository.revokeFamily(token.getFamilyId(), now, REASON_LOGOUT);
    }
  }

  /**
   * Revokes every family of a user except the one named, so a password change signs out the other
   * devices without signing out the one performing it.
   */
  @Transactional
  public void revokeOtherFamilies(UUID userId, UUID keepFamilyId) {
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    repository.revokeAllForUserExceptFamily(userId, keepFamilyId, now, REASON_PASSWORD_CHANGED);
  }

  /** The family a presented token belongs to, if it is known at all. */
  @Transactional(readOnly = true)
  public Optional<UUID> familyOf(String presented) {
    return repository.findByTokenDigest(digest(presented)).map(RefreshToken::getFamilyId);
  }

  static String digest(String plaintext) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the platform specification.", e);
    }
  }
}
