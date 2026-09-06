package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.bytelore.server.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Per-IP throttling on the anonymous endpoints (§3.6, §3.7 of the REST contract).
 *
 * <p>The budget is lowered for the duration of one test rather than configured low for the whole
 * suite. Every test in a run shares one client address, so a production-sized limit in the test
 * profile would make a green run depend on how many requests the classes ahead of this one happened
 * to make in the same minute -- a failure that appears and disappears with execution order. The
 * counter row is cleared first for the same reason: this test asserts about the requests it makes,
 * not about the ones that came before it.
 */
class AnonymousReadRateLimitIT extends ContentApiTestSupport {

  private static final int LOWERED_LIMIT = 2;

  @Autowired private RateLimitProperties rateLimits;

  private int originalManifestLimit;

  @AfterEach
  void restoreLimitsAndCounters() {
    rateLimits.setManifestPerMinute(originalManifestLimit);
    clearManifestCounters();
  }

  @Test
  void aCallerOverTheManifestBudgetIsThrottledWithRetryAfter() throws Exception {
    originalManifestLimit = rateLimits.getManifestPerMinute();
    clearManifestCounters();
    rateLimits.setManifestPerMinute(LOWERED_LIMIT);

    for (int attempt = 1; attempt <= LOWERED_LIMIT; attempt++) {
      MvcResult allowed = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();
      assertThat(allowed.getResponse().getStatus())
          .withFailMessage("request %d of %d was throttled early", attempt, LOWERED_LIMIT)
          .isEqualTo(200);
    }

    MvcResult throttled = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();

    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
    // The engine treats 429 as a wait, so the wait has to be stated.
    assertThat(Integer.parseInt(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
        .isBetween(1, 60);
  }

  /**
   * The two families have separate budgets. Exhausting the manifest budget must not stop a library
   * download that is already in flight: the ratio between the limits exists precisely because a
   * client reads a few manifests and then fetches many packages.
   */
  @Test
  void theContentBudgetIsSeparateFromTheManifestBudget() throws Exception {
    originalManifestLimit = rateLimits.getManifestPerMinute();
    clearManifestCounters();
    rateLimits.setManifestPerMinute(1);

    assertThat(
            mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(
            mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn().getResponse().getStatus())
        .isEqualTo(429);

    // A package request with a nonexistent id still reaches the controller: it is answered by the
    // protocol's own 404, not by the manifest family's exhausted budget.
    MvcResult content =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + java.util.UUID.randomUUID()).param("version", "1"))
            .andReturn();
    assertThat(content.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(content)).isEqualTo("LESSON_NOT_FOUND");
  }

  private void clearManifestCounters() {
    new JdbcTemplate(dataSource)
        .update("DELETE FROM rate_limit_counters WHERE bucket_key LIKE 'manifest:%'");
  }
}
