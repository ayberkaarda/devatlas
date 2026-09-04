package dev.devatlas.server.auth;

import dev.devatlas.server.auth.dto.AuthResponse;
import dev.devatlas.server.auth.dto.ChangePasswordRequest;
import dev.devatlas.server.auth.dto.LoginRequest;
import dev.devatlas.server.auth.dto.LogoutRequest;
import dev.devatlas.server.auth.dto.RefreshRequest;
import dev.devatlas.server.auth.dto.RegisterRequest;
import dev.devatlas.server.auth.dto.UpdateMeRequest;
import dev.devatlas.server.auth.dto.UserResponse;
import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.config.AuthProperties;
import dev.devatlas.server.web.RequestIdFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authentication surface.
 *
 * <p>Refresh tokens reach the two clients differently -- a browser gets a cookie it cannot read, a
 * desktop client gets a value it stores itself -- and the rule that picks between them lives here.
 * The choice is made <strong>once, at sign-in</strong>. Refresh and sign-out never accept a
 * delivery mode; they answer on whichever channel the request arrived on. That asymmetry is the
 * whole defence: a refresh endpoint that honoured a caller-chosen mode would let injected script
 * ask for the body form, the browser would attach the HttpOnly cookie for it, and the server would
 * hand back the plaintext of a credential the browser was specifically built not to expose.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String DELIVERY_BODY = "BODY";
  private static final String TOKEN_TYPE = "Bearer";

  private final AuthService authService;
  private final AuthProperties properties;

  public AuthController(AuthService authService, AuthProperties properties) {
    this.authService = authService;
    this.properties = properties;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthService.AuthOutcome outcome = authService.register(request, request.deviceLabel());
    return tokenResponse(outcome, isBodyDelivery(request.tokenDelivery()), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthOutcome outcome = authService.login(request, request.deviceLabel());
    return tokenResponse(outcome, isBodyDelivery(request.tokenDelivery()), HttpStatus.OK);
  }

  /**
   * Exchanges a refresh token. The {@code Authorization} header is ignored entirely: a client whose
   * access token expired while it was offline has to be able to recover, and requiring a valid
   * access token here would make recovery impossible for exactly the caller who needs it.
   */
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
    PresentedToken presented =
        readPresentedToken(request == null ? null : request.refreshToken(), httpRequest);
    AuthService.AuthOutcome outcome =
        authService.refresh(presented.value(), clientContext(httpRequest));
    return tokenResponse(outcome, presented.fromBody(), HttpStatus.OK);
  }

  /**
   * Revokes the presented token's family, or every family of its owner.
   *
   * <p>Always answers with no content, whether the token was live, already revoked, expired or
   * entirely unknown -- signing out must not become an oracle for whether a token is still good.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) LogoutRequest request,
      @RequestParam(name = "all_devices", defaultValue = "false") boolean allDevices,
      HttpServletRequest httpRequest) {
    PresentedToken presented =
        readPresentedToken(request == null ? null : request.refreshToken(), httpRequest);
    authService.logout(presented.value(), allDevices);

    ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
    if (!presented.fromBody()) {
      response.header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString());
    }
    return response.build();
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AccessTokenClaims caller) {
    return authService.currentUser(caller.userId());
  }

  @PatchMapping("/me")
  public UserResponse updateMe(
      @AuthenticationPrincipal AccessTokenClaims caller,
      @Valid @RequestBody UpdateMeRequest request) {
    return authService.updatePreferences(caller.userId(), request);
  }

  /**
   * Changes the caller's password.
   *
   * <p>A wrong current password is a fact about the request body, not about the session, so it is
   * reported as a semantic failure rather than an authentication one. Clients run a global
   * interceptor that reads an authentication failure as "refresh and retry"; answering that way
   * here would send it looping against a request that can never succeed and end in a spurious
   * sign-out instead of "your current password is wrong".
   *
   * <p>Identifying which family belongs to the caller -- so it can be spared from the revocation
   * below -- needs the caller's own refresh token, and that token does not always arrive in a
   * cookie: a caller using {@code BODY} token delivery (§3.8), the desktop client, never has one.
   * {@code request.refreshToken()} is checked first for exactly that reason, falling back to the
   * cookie for the web build. There is no ambiguity error for this endpoint the way there is on
   * {@code /refresh} and {@code /logout}: this token is never echoed back to a caller, so a client
   * that mistakenly sent both has nothing to gain by it, and the body value is simply preferred.
   */
  @PostMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AccessTokenClaims caller,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest) {
    String callerRefreshToken =
        request.refreshToken() != null && !request.refreshToken().isBlank()
            ? request.refreshToken()
            : refreshCookieValue(httpRequest);
    authService.changePassword(caller.userId(), request, callerRefreshToken);
    return ResponseEntity.noContent().build();
  }

  /** A refresh token as it was presented, and on which channel. */
  private record PresentedToken(String value, boolean fromBody) {}

  /**
   * Reads the refresh token from the body or from the cookie, and refuses to choose when both are
   * present. Guessing there would be guessing about a credential.
   */
  private PresentedToken readPresentedToken(String fromBody, HttpServletRequest request) {
    String fromCookie = refreshCookieValue(request);
    boolean hasBody = fromBody != null && !fromBody.isBlank();
    boolean hasCookie = fromCookie != null && !fromCookie.isBlank();

    if (hasBody && hasCookie) {
      throw new ApiException(
          ErrorCode.AMBIGUOUS_TOKEN_DELIVERY,
          "A refresh token was supplied in both the request body and the cookie.");
    }
    if (hasBody) {
      return new PresentedToken(fromBody, true);
    }
    if (hasCookie) {
      return new PresentedToken(fromCookie, false);
    }
    throw new ApiException(
        ErrorCode.VALIDATION_FAILED, "No refresh token was supplied on either channel.");
  }

  private ResponseEntity<AuthResponse> tokenResponse(
      AuthService.AuthOutcome outcome, boolean bodyDelivery, HttpStatus status) {
    AuthResponse body =
        new AuthResponse(
            outcome.accessToken(),
            outcome.accessTokenExpiresAt(),
            // Present but null in cookie mode. The expiry is returned either way, so a client can
            // schedule its next refresh without ever reading the token itself.
            bodyDelivery ? outcome.refreshToken() : null,
            outcome.refreshTokenExpiresAt(),
            TOKEN_TYPE,
            outcome.user());
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (!bodyDelivery) {
      builder.header(HttpHeaders.SET_COOKIE, refreshCookie(outcome.refreshToken()).toString());
    }
    return builder.body(body);
  }

  private static boolean isBodyDelivery(String tokenDelivery) {
    return DELIVERY_BODY.equals(tokenDelivery);
  }

  private String refreshCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (properties.getCookieName().equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseCookie refreshCookie(String value) {
    return baseCookie(value).maxAge(properties.getRefreshTtl()).build();
  }

  private ResponseCookie clearedRefreshCookie() {
    return baseCookie("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(properties.getCookieName(), value)
        .httpOnly(true)
        .secure(properties.isCookieSecure())
        // Strict, so a cross-site request never carries the credential at all. The cost is a
        // deployment constraint: the browser build and this API must be same-site, or the cookie is
        // simply never sent and the failure presents as an empty cookie jar.
        .sameSite("Strict")
        // The path covers the whole auth group rather than just the refresh endpoint, so sign-out
        // receives the cookie too -- logout has to be able to revoke the token it is asked about.
        .path(properties.getCookiePath());
  }

  private ClientContext clientContext(HttpServletRequest request) {
    return new ClientContext(request.getRemoteAddr(), request.getHeader(RequestIdFilter.HEADER));
  }
}
