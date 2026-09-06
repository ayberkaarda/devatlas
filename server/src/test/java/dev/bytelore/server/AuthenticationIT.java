package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.bytelore.server.auth.RefreshTokenFamilyRevoker;
import dev.bytelore.server.auth.dto.AuthResponse;
import dev.bytelore.server.auth.dto.ChangePasswordRequest;
import dev.bytelore.server.auth.dto.LoginRequest;
import dev.bytelore.server.auth.dto.LogoutRequest;
import dev.bytelore.server.auth.dto.RefreshRequest;
import dev.bytelore.server.auth.dto.RegisterRequest;
import dev.bytelore.server.config.AuthProperties;
import dev.bytelore.server.domain.Role;
import dev.bytelore.server.web.ErrorResponse;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The authentication surface, exercised end to end against a real Postgres Testcontainer: register
 * and sign in, refresh rotation and its reuse-detection and grace-window rules, password change and
 * its family revocation, and sign-out.
 *
 * <p>Assertions are written against {@code docs/protocol/rest-api.md} sections 3 and 6 -- token
 * shapes, rotation rules and the error catalogue -- not against the current shape of the service
 * classes. Where the two disagree, that disagreement is the more important finding and is called
 * out in the Javadoc of the test that found it, rather than silently adjusted to match whatever the
 * code currently does.
 *
 * <p>The refresh-rotation grace window is exercised by moving a fake clock rather than sleeping:
 * see {@link MutableClock} and {@link ClockTestConfiguration} below.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIT {

  private static final String PASSWORD = "correct-horse-battery-2026";
  private static final String NEW_PASSWORD = "new-correct-horse-battery-2027";

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockTestConfiguration {

    /**
     * Marked {@code @Primary} so it wins over the production {@code systemClock} bean without
     * redefining it -- the two beans have different names, so this is an additional candidate, not
     * an override, and needs no {@code spring.main.allow-bean-overriding}.
     */
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(Instant.now());
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private MutableClock clock;
  @Autowired private AuthProperties authProperties;
  @Autowired private RefreshTokenFamilyRevoker familyRevoker;

  @BeforeEach
  void resetClock() {
    // The Spring context -- and the singleton clock bean inside it -- is cached and reused across
    // test methods. Without an explicit reset here, a grace-window test that advances the clock
    // would leave every later test running against a clock that no longer reads "now".
    clock.set(Instant.now());
  }

  @Test
  void registerReturnsAnAccessTokenARefreshTokenAndAUserWhoseClaimsMatchTheContract()
      throws Exception {
    String email = uniqueEmail();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new RegisterRequest(
                                email, PASSWORD, null, null, "primary-device", "BODY"))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);

    String raw = result.getResponse().getContentAsString();
    AuthResponse session = jsonMapper.readValue(raw, AuthResponse.class);

    assertThat(session.accessToken()).isNotBlank();
    assertThat(session.refreshToken()).isNotBlank();
    assertThat(session.tokenType()).isEqualTo("Bearer");
    assertThat(session.user().email()).isEqualTo(email);
    assertThat(session.user().role()).isEqualTo(Role.USER);

    // Every timestamp this API emits follows one exact pattern: UTC, a literal Z, exactly three
    // fractional digits always present.
    String timestampPattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";
    assertThat(jsonMapper.readTree(raw).path("access_token_expires_at").asString())
        .matches(timestampPattern);
    assertThat(jsonMapper.readTree(raw).path("refresh_token_expires_at").asString())
        .matches(timestampPattern);

    SignedJWT jwt = SignedJWT.parse(session.accessToken());
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getSubject()).isEqualTo(session.user().id().toString());
    assertThat(claims.getStringClaim("role")).isEqualTo("USER");
    assertThat(claims.getStringClaim("typ")).isEqualTo("access");
    assertThat(claims.getJWTID()).isNotBlank();
    assertThatCode(() -> UUID.fromString(claims.getJWTID())).doesNotThrowAnyException();
    assertThat(claims.getIssueTime()).isNotNull();
    assertThat(claims.getExpirationTime().toInstant()).isEqualTo(session.accessTokenExpiresAt());
    assertThat(
            Duration.between(
                claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
        .isEqualTo(authProperties.getAccessTtl());
  }

  @Test
  void accessTokenAuthorizesAProtectedEndpointAndAMissingOneIsRejected() throws Exception {
    String email = uniqueEmail();
    AuthResponse session = register(email, PASSWORD, "device-a");

    MvcResult authorized =
        mockMvc
            .perform(
                get("/api/v1/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
            .andReturn();
    assertThat(authorized.getResponse().getStatus()).isEqualTo(200);
    assertThat(
            jsonMapper
                .readTree(authorized.getResponse().getContentAsString())
                .path("email")
                .asString())
        .isEqualTo(email);

    MvcResult missing = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
    assertThat(missing.getResponse().getStatus()).isEqualTo(401);
    ErrorResponse error =
        jsonMapper.readValue(missing.getResponse().getContentAsString(), ErrorResponse.class);
    assertThat(error.code()).isEqualTo("AUTH_REQUIRED");
  }

  /**
   * Plain single-use rotation, isolated from the reuse-detection and grace-window machinery
   * exercised by the tests below: presenting the same already-rotated token a second time is
   * ambiguous by design (it can succeed via the grace path or fail via reuse detection, depending
   * on timing and on whether the successor has moved on), so this test proves "no longer works" the
   * one way that is unambiguous regardless of timing -- outside the grace window, a second
   * presentation is always rejected.
   */
  @Test
  void refreshRotatesToANewRefreshTokenAndTheOldOneStopsWorkingOutsideTheGraceWindow()
      throws Exception {
    AuthResponse session = register(uniqueEmail(), PASSWORD, "device-a");
    String original = session.refreshToken();

    AuthResponse rotated = refresh(original);
    assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(original);
    assertThat(rotated.accessToken()).isNotBlank();

    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.accessToken()))
        .andExpect(status().isOk());

    clock.advance(authProperties.getRefreshGrace().plusSeconds(1));

    expectRefreshError(original, 401, "REFRESH_TOKEN_REUSED");
  }

  /**
   * Reuse of a rotated token outside the grace window revokes the whole family, including the token
   * that -- a moment before this request -- was the legitimate, currently valid one.
   *
   * <p>The revoke and the {@code REFRESH_TOKEN_REUSED} throw both happen inside {@code
   * RefreshTokenService#handleAlreadyRotated}. The revoke itself runs through {@link
   * RefreshTokenFamilyRevoker} in a transaction of its own, committed before the throw, precisely
   * so that the rollback the exception triggers on the enclosing transaction cannot take the revoke
   * back with it -- see that class's Javadoc, and {@link
   * #familyRevocationRunsThroughARealSpringProxySoRequiresNewActuallyApplies} below for the check
   * that it is actually wired that way.
   */
  @Test
  void reuseOfARotatedTokenOutsideTheGraceWindowRevokesTheWholeFamily() throws Exception {
    AuthResponse session = register(uniqueEmail(), PASSWORD, "device-a");
    String original = session.refreshToken();

    AuthResponse rotated = refresh(original);
    String successor = rotated.refreshToken();

    clock.advance(authProperties.getRefreshGrace().plusSeconds(1));

    expectRefreshError(original, 401, "REFRESH_TOKEN_REUSED");
    // The successor was perfectly valid a moment ago. Asserting only the 401 above would pass even
    // if family-wide revocation were broken and only the replayed token itself had been killed.
    expectRefreshError(successor, 401, "REFRESH_TOKEN_INVALID");
  }

  /**
   * A rotation response that never reached the client looks identical, from the server's side, to
   * theft: the same token is presented a second time. Inside the 30-second grace window, with the
   * successor never having been used, the server treats this as a lost response: it cannot return
   * the successor again (only its digest was ever stored), so it revokes that unused successor and
   * mints an entirely new pair in the same family.
   */
  @Test
  void presentingARotatedTokenInsideTheGraceWindowWithAnUnusedSuccessorReMintsAFreshPair()
      throws Exception {
    AuthResponse session = register(uniqueEmail(), PASSWORD, "device-a");
    String original = session.refreshToken();

    AuthResponse rotated = refresh(original);
    String unusedSuccessor = rotated.refreshToken();

    // Still inside the 30-second window (the clock has not moved), and the successor above has
    // never itself been presented to /refresh.
    AuthResponse reminted = refresh(original);

    assertThat(reminted.refreshToken()).isNotBlank();
    assertThat(reminted.refreshToken()).isNotEqualTo(original);
    assertThat(reminted.refreshToken()).isNotEqualTo(unusedSuccessor);

    // The un-used successor is now dead. A test that only checked the 200 above would pass even if
    // the server had left two live tokens in the family, which is exactly the state the grace path
    // exists to avoid.
    expectRefreshError(unusedSuccessor, 401, "REFRESH_TOKEN_INVALID");

    // The family survived the grace re-mint: the freshly issued pair still works normally.
    AuthResponse next = refresh(reminted.refreshToken());
    assertThat(next.refreshToken()).isNotBlank();
  }

  /**
   * The grace window only covers a lost response, not a chain that has genuinely moved on. Once the
   * successor itself has been exchanged, presenting the original a second time -- even still inside
   * the 30-second window -- is unambiguous reuse, and the whole family is revoked, including the
   * token that was valid a moment before this request.
   */
  @Test
  void graceWindowDoesNotApplyWhenTheSuccessorHasAlreadyBeenUsed() throws Exception {
    AuthResponse session = register(uniqueEmail(), PASSWORD, "device-a");
    String original = session.refreshToken();

    AuthResponse rotated = refresh(original);
    String successor = rotated.refreshToken();

    AuthResponse afterSuccessor = refresh(successor);
    String grandchild = afterSuccessor.refreshToken();

    expectRefreshError(original, 401, "REFRESH_TOKEN_REUSED");
    expectRefreshError(grandchild, 401, "REFRESH_TOKEN_INVALID");
  }

  /**
   * Architectural check backing the two tests above: {@code REQUIRES_NEW} only takes effect when a
   * call actually crosses the Spring AOP proxy. A same-class call (self-invocation) bypasses the
   * proxy and silently ignores the annotation, which would look identical to a correct fix while
   * fixing nothing -- passing behavioural tests alone would not rule that out, since the outer
   * transaction is never rolled back on the success paths those tests otherwise exercise. This test
   * asserts directly that the bean {@link RefreshTokenService} calls is the proxy, not the raw
   * target, so {@code REQUIRES_NEW} is guaranteed to apply.
   */
  @Test
  void familyRevocationRunsThroughARealSpringProxySoRequiresNewActuallyApplies() {
    assertThat(AopUtils.isAopProxy(familyRevoker)).isTrue();
    assertThat(AopUtils.isCglibProxy(familyRevoker)).isTrue();
  }

  /**
   * A correct password change revokes every other family belonging to the user but not the one
   * making the request, and a wrong current password is reported as a semantic 422, never a 401 --
   * see the Javadoc on {@code AuthController#changePassword} for why a 401 there would be worse
   * than merely wrong.
   *
   * <p>The controller can only identify "the family the caller is currently using" through the
   * refresh cookie (see {@code AuthController#changePassword} and {@code #refreshCookieValue}), so
   * this test signs in with {@code token_delivery: COOKIE} to exercise that identification path.
   */
  @Test
  void passwordChangeRevokesOtherFamiliesButKeepsTheCallersOwnUnderCookieDelivery()
      throws Exception {
    String email = uniqueEmail();

    MvcResult registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new RegisterRequest(
                                email, PASSWORD, null, null, "device-a", "COOKIE"))))
            .andReturn();
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Cookie callerCookie = registerResult.getResponse().getCookie(authProperties.getCookieName());
    assertThat(callerCookie).isNotNull();
    AuthResponse callerSession =
        jsonMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponse.class);
    // Cookie delivery omits the token from the body; the cookie is the only place it lives.
    assertThat(callerSession.refreshToken()).isNull();

    // A second device, signed in over BODY delivery, is the family that must be revoked.
    AuthResponse otherDevice = login(email, PASSWORD, "device-b");
    String otherFamilyRefreshToken = otherDevice.refreshToken();

    MvcResult wrongPassword =
        mockMvc
            .perform(
                post("/api/v1/auth/me/password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerSession.accessToken())
                    .cookie(callerCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ChangePasswordRequest(
                                "definitely-the-wrong-password", NEW_PASSWORD, null))))
            .andReturn();
    assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(422);
    ErrorResponse wrongError =
        jsonMapper.readValue(wrongPassword.getResponse().getContentAsString(), ErrorResponse.class);
    assertThat(wrongError.code()).isEqualTo("CURRENT_PASSWORD_INCORRECT");

    MvcResult changed =
        mockMvc
            .perform(
                post("/api/v1/auth/me/password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerSession.accessToken())
                    .cookie(callerCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ChangePasswordRequest(PASSWORD, NEW_PASSWORD, null))))
            .andReturn();
    assertThat(changed.getResponse().getStatus()).isEqualTo(204);

    // The other device is signed out.
    expectRefreshError(otherFamilyRefreshToken, 401, "REFRESH_TOKEN_INVALID");

    // The caller's own family survives: its refresh cookie can still be exchanged.
    MvcResult callerRefresh =
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(callerCookie)).andReturn();
    assertThat(callerRefresh.getResponse().getStatus()).isEqualTo(200);
  }

  /**
   * §5.1.7 says password change "revokes every refresh-token family belonging to the user
   * <em>except the one the caller is currently using</em>" with no carve-out for delivery mode. A
   * caller using {@code BODY} token delivery -- the desktop client, by the table in §3.8 -- never
   * has a refresh cookie, so it has to be able to name its own family some other way: {@code
   * ChangePasswordRequest.refreshToken()} is that channel, mirroring {@link
   * dev.bytelore.server.auth.dto.LogoutRequest}. Without it, the caller's own session would be
   * revoked by the very request that changed its password.
   */
  @Test
  void passwordChangeUnderBodyDeliveryKeepsTheCallersOwnSessionUsingTheBodyRefreshToken()
      throws Exception {
    String email = uniqueEmail();
    AuthResponse session = register(email, PASSWORD, "device-a");

    MvcResult changed =
        mockMvc
            .perform(
                post("/api/v1/auth/me/password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ChangePasswordRequest(
                                PASSWORD, NEW_PASSWORD, session.refreshToken()))))
            .andReturn();
    assertThat(changed.getResponse().getStatus()).isEqualTo(204);

    // The caller's own session survives: its BODY-delivered refresh token still works.
    AuthResponse afterChange = refresh(session.refreshToken());
    assertThat(afterChange.refreshToken()).isNotBlank();
  }

  @Test
  void logoutIsPublicAndWorksWithAnAbsentOrExpiredAccessToken() throws Exception {
    // Case 1: no Authorization header at all -- the client never signed in, or the header was
    // simply not sent. The refresh token in the body is the only credential logout needs.
    AuthResponse sessionA = register(uniqueEmail(), PASSWORD, "device-a");
    MvcResult logoutA =
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(new LogoutRequest(sessionA.refreshToken()))))
            .andReturn();
    assertThat(logoutA.getResponse().getStatus()).isEqualTo(204);
    expectRefreshError(sessionA.refreshToken(), 401, "REFRESH_TOKEN_INVALID");

    // Case 2: an access token that has since expired is presented anyway and must not block
    // logout -- this is exactly the offline-desktop scenario the contract is built around.
    AuthResponse sessionB = register(uniqueEmail(), PASSWORD, "device-b");
    clock.advance(authProperties.getAccessTtl().plusSeconds(1));
    MvcResult logoutB =
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionB.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(new LogoutRequest(sessionB.refreshToken()))))
            .andReturn();
    assertThat(logoutB.getResponse().getStatus()).isEqualTo(204);
    expectRefreshError(sessionB.refreshToken(), 401, "REFRESH_TOKEN_INVALID");
  }

  private static String uniqueEmail() {
    return "auth-it-" + UUID.randomUUID() + "@example.test";
  }

  private AuthResponse register(String email, String password, String deviceLabel)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new RegisterRequest(email, password, null, null, deviceLabel, "BODY"))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
  }

  private AuthResponse login(String email, String password, String deviceLabel) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new LoginRequest(email, password, deviceLabel, "BODY"))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
  }

  private MvcResult refreshRaw(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RefreshRequest(refreshToken))))
        .andReturn();
  }

  private AuthResponse refresh(String refreshToken) throws Exception {
    MvcResult result = refreshRaw(refreshToken);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
  }

  private void expectRefreshError(String refreshToken, int expectedStatus, String expectedCode)
      throws Exception {
    MvcResult result = refreshRaw(refreshToken);
    assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    ErrorResponse error =
        jsonMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
    assertThat(error.code()).isEqualTo(expectedCode);
  }
}
