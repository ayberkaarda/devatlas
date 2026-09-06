package dev.bytelore.server.auth;

import dev.bytelore.server.auth.dto.ChangePasswordRequest;
import dev.bytelore.server.auth.dto.LoginRequest;
import dev.bytelore.server.auth.dto.RegisterRequest;
import dev.bytelore.server.auth.dto.UpdateMeRequest;
import dev.bytelore.server.auth.dto.UserResponse;
import dev.bytelore.server.auth.dto.UserSummaryResponse;
import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.domain.Role;
import dev.bytelore.server.domain.Theme;
import dev.bytelore.server.domain.User;
import dev.bytelore.server.domain.UserLocale;
import dev.bytelore.server.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, sign-in, token exchange, sign-out and the two self-service profile operations. */
@Service
public class AuthService {

  private final UserRepository users;
  private final RefreshTokenService refreshTokens;
  private final JwtService jwt;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper mapper;
  private final Clock clock;

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
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.jwt = jwt;
    this.passwordEncoder = passwordEncoder;
    this.mapper = mapper;
    this.clock = clock;
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

  @Transactional
  public AuthOutcome login(LoginRequest request, String deviceLabel) {
    String email = normalizeEmail(request.email());
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
