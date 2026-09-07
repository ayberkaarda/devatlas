package dev.bytelore.server.auth;

import dev.bytelore.server.auth.dto.ChangePasswordRequest;
import dev.bytelore.server.auth.dto.LoginRequest;
import dev.bytelore.server.auth.dto.RegisterRequest;
import dev.bytelore.server.auth.dto.UpdateMeRequest;
import dev.bytelore.server.auth.dto.UserResponse;
import dev.bytelore.server.auth.dto.UserSummaryResponse;
import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.RateLimitedException;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.content.packaging.Sha256;
import dev.bytelore.server.domain.Role;
import dev.bytelore.server.domain.Theme;
import dev.bytelore.server.domain.User;
import dev.bytelore.server.domain.UserLocale;
import dev.bytelore.server.ratelimit.FixedWindowRateLimiter;
import dev.bytelore.server.ratelimit.RateLimitProperties;
import dev.bytelore.server.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, sign-in, token exchange, sign-out and the two self-service profile operations.
 *
 * <p>Three of the five authentication rate limits of §3.6 are enforced here rather than in a
 * filter, because their keys do not exist until this layer runs: sign-in is keyed partly by an
 * email address that is unparsed bytes at the filter boundary, and token exchange and password
 * change are keyed by a user who is only known once the presented credential has been resolved. The
 * two limits keyed purely by the connection are enforced before any of this, in {@code
 * AnonymousAuthRateLimitFilter}.
 */
@Service
public class AuthService {

  private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
  private static final Duration HOURLY_WINDOW = Duration.ofHours(1);

  private final UserRepository users;
  private final RefreshTokenService refreshTokens;
  private final JwtService jwt;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper mapper;
  private final Clock clock;
  private final FixedWindowRateLimiter rateLimiter;
  private final RateLimitProperties rateLimits;

  /**
   * A hash of a value nobody knows, verified against when the email is unknown so that a failed
   * lookup costs the same as a failed password check. Without it, "no such account" answers
   * measurably faster than "wrong password" and the endpoint becomes a way to enumerate users.
   */
  private final String timingEqualizationHash;

  public AuthService(
      UserRepository users,
      RefreshTokenService refreshTokens,
      JwtService jwt,
      PasswordEncoder passwordEncoder,
      UserMapper mapper,
      Clock clock,
      FixedWindowRateLimiter rateLimiter,
      RateLimitProperties rateLimits) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.jwt = jwt;
    this.passwordEncoder = passwordEncoder;
    this.mapper = mapper;
    this.clock = clock;
    this.rateLimiter = rateLimiter;
    this.rateLimits = rateLimits;
    this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Everything a successful authentication produces. The refresh token is carried in the clear here
   * because the caller has to decide whether it goes into the body or into a cookie; it is the last
   * moment the plaintext exists anywhere.
   */
  public record AuthOutcome(
      String accessToken,
      Instant accessTokenExpiresAt,
      String refreshToken,
      Instant refreshTokenExpiresAt,
      UserSummaryResponse user) {}

  @Transactional
  public AuthOutcome register(RegisterRequest request, String deviceLabel) {
    String email = normalizeEmail(request.email());
    if (users.existsByEmail(email)) {
      throw new ApiException(
          ErrorCode.EMAIL_ALREADY_REGISTERED, "An account already exists for this email address.");
    }

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    User user = new User();
    user.setId(UuidV7.randomUuid());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    // Never read from the request. A client cannot ask to be an administrator.
    user.setRole(Role.USER);
    user.setLocale(
        request.locale() == null ? UserLocale.EN : UserLocale.fromCode(request.locale()));
    user.setTheme(request.theme() == null ? Theme.SYSTEM : Theme.valueOf(request.theme()));
    user.setEnabled(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    users.save(user);

    return issueSession(user, null, deviceLabel);
  }

  /**
   * Signs in, throttled per email address and client address together (§3.6).
   *
   * <p>The budget is spent before the password is checked, so a wrong guess costs an attempt. That
   * is the entire point: a limit charged only on success would count the one caller who does not
   * need limiting.
   *
   * <p>The email is hashed into the bucket key rather than written into it. Two reasons, and both
   * would be found the hard way: the counter table's key column is far shorter than the 254
   * characters an email address may occupy, so a long address would overflow it and -- because the
   * limiter treats a database fault as "allow" -- would silently exempt itself; and a table of
   * operational counters is no place to accumulate a list of which addresses people tried to sign
   * in with.
   */
  @Transactional
  public AuthOutcome login(LoginRequest request, String deviceLabel, ClientContext context) {
    String email = normalizeEmail(request.email());
    requireBudget(
        "auth-login:%s:%s"
            .formatted(
                Sha256.hex(String.valueOf(email).getBytes(StandardCharsets.UTF_8)),
                context.clientIp()),
        rateLimits.getLoginPer15Minutes(),
        LOGIN_WINDOW,
        "Too many sign-in attempts for this account from this address; retry later.");

    User user = users.findByEmail(email).orElse(null);
    if (user == null) {
      passwordEncoder.matches(request.password(), timingEqualizationHash);
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }
    if (!user.isEnabled()) {
      throw accountDisabled();
    }
    return issueSession(user, null, deviceLabel);
  }

  @Transactional
  public AuthOutcome refresh(String presentedRefreshToken, ClientContext context) {
    RefreshTokenService.RotationResult rotation =
        refreshTokens.rotate(presentedRefreshToken, context);
    // Charged after the token resolves, because the contract keys this budget by user and there is
    // no user before then. A presented token that resolves to nobody is refused by the rotation
    // itself and never reaches a bucket.
    requireBudget(
        "auth-refresh:" + rotation.userId(),
        rateLimits.getRefreshPerHour(),
        HOURLY_WINDOW,
        "Too many token exchanges for this account; retry later.");
    User user =
        users
            .findById(rotation.userId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.REFRESH_TOKEN_INVALID,
                        "The account this token belongs to no longer exists."));
    if (!user.isEnabled()) {
      throw accountDisabled();
    }
    JwtService.IssuedAccessToken access = jwt.issue(user);
    return new AuthOutcome(
        access.token(),
        access.expiresAt(),
        rotation.refresh().token(),
        rotation.refresh().stored().getExpiresAt(),
        mapper.toSummary(user));
  }

  /**
   * Revokes the presented token's family, or every family of its owner.
   *
   * <p>Returns nothing and reports nothing, whether the token was live, already revoked, expired or
   * entirely unknown. Sign-out is unauthenticated, so any difference in its answer would be a free
   * check on whether a token is still good.
   */
  @Transactional
  public void logout(String presentedRefreshToken, boolean allDevices) {
    refreshTokens.revokeByPresentedToken(presentedRefreshToken, allDevices);
  }

  @Transactional(readOnly = true)
  public UserResponse currentUser(UUID userId) {
    return mapper.toResponse(requireUser(userId));
  }

  @Transactional
  public UserResponse updatePreferences(UUID userId, UpdateMeRequest request) {
    User user = requireUser(userId);
    if (request.locale() != null) {
      UserLocale locale = UserLocale.fromCode(request.locale());
      if (locale == null) {
        throw new ApiException(
            ErrorCode.UNSUPPORTED_LOCALE,
            "Locale '%s' is not one of en, tr, fr, de.".formatted(request.locale()));
      }
      user.setLocale(locale);
    }
    if (request.theme() != null) {
      user.setTheme(Theme.valueOf(request.theme()));
    }
    user.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    return mapper.toResponse(users.save(user));
  }

  /**
   * Changes the caller's password and signs out their other devices.
   *
   * <p>{@code callerRefreshToken} is the refresh token that arrived alongside this request, when
   * one did. A browser sends it automatically, because the cookie's path covers this endpoint; a
   * client that keeps its token itself sends nothing, and there is no other way to learn which
   * family it is using -- the access token deliberately carries no family identifier. With a family
   * known, every other family is revoked and the calling device stays signed in; without one, every
   * family is revoked, which is the safe direction to fail in.
   */
  @Transactional
  public void changePassword(
      UUID userId, ChangePasswordRequest request, String callerRefreshToken) {
    // Before the current password is verified, so a wrong guess costs an attempt. This endpoint is
    // reachable with nothing but a stolen access token, which makes it the cheapest place to
    // brute-force a password that the API has.
    requireBudget(
        "auth-password:" + userId,
        rateLimits.getPasswordChangePerHour(),
        HOURLY_WINDOW,
        "Too many password changes for this account; retry later.");

    User user = requireUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          ErrorCode.CURRENT_PASSWORD_INCORRECT, "The current password does not match.");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    users.save(user);

    UUID keepFamilyId =
        callerRefreshToken == null ? null : refreshTokens.familyOf(callerRefreshToken).orElse(null);
    refreshTokens.revokeOtherFamilies(userId, keepFamilyId);
  }

  private AuthOutcome issueSession(User user, UUID familyId, String deviceLabel) {
    JwtService.IssuedAccessToken access = jwt.issue(user);
    RefreshTokenService.IssuedRefreshToken refresh =
        refreshTokens.issue(user.getId(), familyId, deviceLabel, false);
    return new AuthOutcome(
        access.token(),
        access.expiresAt(),
        refresh.token(),
        refresh.stored().getExpiresAt(),
        mapper.toSummary(user));
  }

  private User requireUser(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "No such account."));
  }

  /**
   * Spends one request from a bucket, or refuses with the wait in seconds. The counter commits in
   * its own transaction, so an attempt still counts when the request it belongs to rolls back.
   */
  private void requireBudget(String bucketKey, int limit, Duration window, String message) {
    FixedWindowRateLimiter.Decision decision = rateLimiter.record(bucketKey, limit, window);
    if (!decision.allowed()) {
      throw new RateLimitedException(message, decision.retryAfterSeconds());
    }
  }

  private static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private static ApiException invalidCredentials() {
    // One message for both an unknown address and a wrong password: telling them apart is telling a
    // stranger which addresses have accounts.
    return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
  }

  private static ApiException accountDisabled() {
    return new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
  }
}
