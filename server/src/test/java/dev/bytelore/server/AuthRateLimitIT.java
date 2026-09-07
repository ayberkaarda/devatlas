package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.bytelore.server.auth.dto.AuthResponse;
import dev.bytelore.server.auth.dto.ChangePasswordRequest;
import dev.bytelore.server.auth.dto.LoginRequest;
import dev.bytelore.server.auth.dto.LogoutRequest;
import dev.bytelore.server.auth.dto.RefreshRequest;
import dev.bytelore.server.auth.dto.RegisterRequest;
import dev.bytelore.server.ratelimit.RateLimitProperties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The five authentication rate limits of §3.6 of the REST contract.
 *
 * <p>Each test lowers only its own budget and clears only its own counter rows, then restores
 * everything. The alternative -- production-sized budgets in the test profile -- would make a green
 * run depend on how many times the classes ahead of this one happened to sign in, since the whole
 * suite shares one client address and one seeded administrator.
 *
 * <p>Two of these tests assert something stronger than "the counter counts". A budget is only worth
 * having if a <em>failed</em> attempt spends it, and every one of these endpoints fails inside a
 * transaction: a wrong password rolls back. A counter written in that same transaction would be
 * rolled back with it, refunding the attacker every attempt and restraining nobody but the person
 * typing correctly.
 */
class AuthRateLimitIT extends ContentApiTestSupport {

  private static final String PASSWORD = "auth-rate-limit-password-2026";

  @Autowired private RateLimitProperties rateLimits;

  private Integer originalLogin;
  private Integer originalRegister;
  private Integer originalRefresh;
  private Integer originalLogout;
  private Integer originalPasswordChange;

  @AfterEach
  void restoreBudgetsAndCounters() {
    if (originalLogin != null) {
      rateLimits.setLoginPer15Minutes(originalLogin);
    }
    if (originalRegister != null) {
      rateLimits.setRegisterPerHour(originalRegister);
    }
    if (originalRefresh != null) {
      rateLimits.setRefreshPerHour(originalRefresh);
    }
    if (originalLogout != null) {
      rateLimits.setLogoutPerHour(originalLogout);
    }
    if (originalPasswordChange != null) {
      rateLimits.setPasswordChangePerHour(originalPasswordChange);
    }
    new JdbcTemplate(dataSource)
        .update("DELETE FROM rate_limit_counters WHERE bucket_key LIKE ?", "auth-%");
  }

  /**
   * Sign-in is keyed by email <em>and</em> address together. Exhausting one account's budget must
   * leave a different account reachable from the same address, or a single mistyped password in an
   * office would lock out the whole floor.
   */
  @Test
  void signInIsThrottledPerAccountAndAddressTogether() throws Exception {
    originalLogin = rateLimits.getLoginPer15Minutes();
    rateLimits.setLoginPer15Minutes(2);
    String email = register();
    String otherEmail = register();

    assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    // A wrong password spends an attempt too, which is the only version of this limit worth having.
    assertThat(login(email, "wrong-password-entirely").getResponse().getStatus()).isEqualTo(401);

    MvcResult throttled = login(email, PASSWORD);
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
    assertThat(Integer.parseInt(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
        .isBetween(1, 900);

    assertThat(login(otherEmail, PASSWORD).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void accountCreationIsThrottledPerAddress() throws Exception {
    originalRegister = rateLimits.getRegisterPerHour();
    rateLimits.setRegisterPerHour(1);

    assertThat(registerResult(uniqueEmail()).getResponse().getStatus()).isEqualTo(201);

    MvcResult throttled = registerResult(uniqueEmail());
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
    assertThat(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
  }

  @Test
  void signOutIsThrottledPerAddress() throws Exception {
    originalLogout = rateLimits.getLogoutPerHour();
    rateLimits.setLogoutPerHour(1);

    assertThat(logout(UUID.randomUUID().toString()).getResponse().getStatus()).isEqualTo(204);

    MvcResult throttled = logout(UUID.randomUUID().toString());
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
  }

  /**
   * Token exchange is keyed by the user the presented token belongs to, which is knowable only
   * after the token has been resolved -- so the budget is charged there rather than at the edge.
   */
  @Test
  void tokenExchangeIsThrottledPerUser() throws Exception {
    originalRefresh = rateLimits.getRefreshPerHour();
    rateLimits.setRefreshPerHour(1);

    AuthResponse session = registerSession(uniqueEmail());
    MvcResult first = refresh(session.refreshToken());
    assertThat(first.getResponse().getStatus()).isEqualTo(200);
    String rotated =
        jsonMapper
            .readValue(first.getResponse().getContentAsString(), AuthResponse.class)
            .refreshToken();

    MvcResult throttled = refresh(rotated);
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
  }

  /**
   * The password change is reachable with nothing but a stolen access token, which makes it the
   * cheapest place in the API to guess a password -- so a wrong {@code current_password} has to
   * cost an attempt. It fails with 422 inside a transaction that then rolls back, and the counter
   * has to survive that rollback; if it did not, this endpoint would offer unlimited guesses.
   */
  @Test
  void aWrongCurrentPasswordStillSpendsThePasswordChangeBudget() throws Exception {
    originalPasswordChange = rateLimits.getPasswordChangePerHour();
    rateLimits.setPasswordChangePerHour(2);

    AuthResponse session = registerSession(uniqueEmail());
    String accessToken = session.accessToken();

    for (int attempt = 1; attempt <= 2; attempt++) {
      MvcResult wrong = changePassword(accessToken, "not-the-current-password");
      assertThat(wrong.getResponse().getStatus())
          .withFailMessage("attempt %d was not the expected semantic failure", attempt)
          .isEqualTo(422);
      assertThat(errorCode(wrong)).isEqualTo("CURRENT_PASSWORD_INCORRECT");
    }

    MvcResult throttled = changePassword(accessToken, PASSWORD);
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
  }

  // -------------------------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------------------------

  private String register() throws Exception {
    String email = uniqueEmail();
    registerSession(email);
    return email;
  }

  private AuthResponse registerSession(String email) throws Exception {
    MvcResult result = registerResult(email);
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(
          "Fixture registration failed: " + result.getResponse().getContentAsString());
    }
    return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
  }

  private MvcResult registerResult(String email) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new RegisterRequest(email, PASSWORD, null, null, "rate-limit-it", "BODY"))))
        .andReturn();
  }

  private MvcResult login(String email, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new LoginRequest(email, password, "rate-limit-it", "BODY"))))
        .andReturn();
  }

  private MvcResult logout(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LogoutRequest(refreshToken))))
        .andReturn();
  }

  private MvcResult refresh(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new RefreshRequest(refreshToken))))
        .andReturn();
  }

  private MvcResult changePassword(String accessToken, String currentPassword) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new ChangePasswordRequest(
                            currentPassword, "a-completely-new-password-2027", null))))
        .andReturn();
  }

  private static String uniqueEmail() {
    return "auth-rate-limit-" + UUID.randomUUID() + "@example.test";
  }
}
