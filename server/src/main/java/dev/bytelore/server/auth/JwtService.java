package dev.bytelore.server.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.config.AuthProperties;
import dev.bytelore.server.domain.Role;
import dev.bytelore.server.domain.User;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived access token.
 *
 * <p>The token is an HS256 JWT. Only this server signs and verifies it, so an asymmetric key pair
 * would add key management and rotation cost without a second consumer to justify it.
 *
 * <p>The {@code typ} claim exists to keep the two credentials apart. Refresh tokens are opaque and
 * are not JWTs at all, but a claim that names what a token is for costs nothing and closes the
 * general shape of attack where a credential minted for one purpose is presented for another.
 */
@Service
public class JwtService {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TYPE = "typ";
  private static final String TYPE_ACCESS = "access";
  private static final int MINIMUM_SECRET_BYTES = 32;

  private final byte[] secret;
  private final AuthProperties properties;
  private final Clock clock;

  public JwtService(AuthProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    String configured = properties.getJwtSecret();
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "bytelore.auth.jwt-secret is not set. Supply it through the environment; there is no"
              + " default, because a default signing key is a key everyone has.");
    }
    this.secret = configured.getBytes(StandardCharsets.UTF_8);
    if (this.secret.length < MINIMUM_SECRET_BYTES) {
      throw new IllegalStateException(
          "bytelore.auth.jwt-secret must be at least %d bytes for HS256; got %d."
              .formatted(MINIMUM_SECRET_BYTES, this.secret.length));
    }
  }

  /** An issued access token together with the moment it stops being accepted. */
  public record IssuedAccessToken(String token, Instant expiresAt) {}

  /** Mints an access token for the given account. */
  public IssuedAccessToken issue(User user) {
    Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = issuedAt.plus(properties.getAccessTtl());
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(user.getId().toString())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UuidV7.randomUuid().toString())
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      jwt.sign(new MACSigner(secret));
    } catch (JOSEException e) {
      throw new IllegalStateException("Access token could not be signed.", e);
    }
    return new IssuedAccessToken(jwt.serialize(), expiresAt);
  }

  /**
   * Verifies a presented access token.
   *
   * @throws ApiException {@code ACCESS_TOKEN_EXPIRED} when only the lifetime failed -- the client
   *     must refresh and retry, never sign out -- or {@code ACCESS_TOKEN_INVALID} for anything
   *     else. The two are distinct codes because a client that cannot tell them apart either signs
   *     users out on an ordinary expiry or retries forever on a broken token.
   */
  public AccessTokenClaims verify(String token) {
    SignedJWT jwt;
    try {
      jwt = SignedJWT.parse(token);
    } catch (ParseException e) {
      throw new ApiException(ErrorCode.ACCESS_TOKEN_INVALID, "Access token is not a valid JWT.", e);
    }

    // Pin the algorithm from the verifier's side. Trusting the header's own claim about how it was
    // signed is the classic way an unsigned or differently signed token gets accepted.
    if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID,
          "Access token is not signed with the expected algorithm.");
    }

    try {
      if (!jwt.verify(new MACVerifier(secret))) {
        throw new ApiException(
            ErrorCode.ACCESS_TOKEN_INVALID, "Access token signature does not verify.");
      }
    } catch (JOSEException e) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID, "Access token signature could not be checked.", e);
    }

    JWTClaimsSet claims;
    try {
      claims = jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID, "Access token claims are malformed.", e);
    }

    String type = asString(claims, CLAIM_TYPE);
    if (!TYPE_ACCESS.equals(type)) {
      throw new ApiException(ErrorCode.ACCESS_TOKEN_INVALID, "Token is not an access token.");
    }

    Date expiration = claims.getExpirationTime();
    if (expiration == null) {
      throw new ApiException(ErrorCode.ACCESS_TOKEN_INVALID, "Access token has no expiry.");
    }
    Instant now = Instant.now(clock);
    if (expiration.toInstant().plus(properties.getClockSkew()).isBefore(now)) {
      throw new ApiException(ErrorCode.ACCESS_TOKEN_EXPIRED, "Access token has expired.");
    }

    UUID userId = parseUuid(claims.getSubject(), "subject");
    UUID tokenId = parseUuid(claims.getJWTID(), "token id");
    Role role;
    try {
      role = Role.valueOf(asString(claims, CLAIM_ROLE));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID, "Access token carries no known role.", e);
    }
    return new AccessTokenClaims(userId, role, tokenId);
  }

  private static String asString(JWTClaimsSet claims, String name) {
    try {
      return claims.getStringClaim(name);
    } catch (ParseException e) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID,
          "Access token claim '%s' is malformed.".formatted(name),
          e);
    }
  }

  private static UUID parseUuid(String value, String what) {
    if (value == null) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID, "Access token has no %s.".formatted(what));
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.ACCESS_TOKEN_INVALID, "Access token %s is not a UUID.".formatted(what), e);
    }
  }
}
