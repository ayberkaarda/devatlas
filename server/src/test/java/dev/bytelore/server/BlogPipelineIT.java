package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.content.admin.dto.AdminBlogPostResponse;
import dev.bytelore.server.content.admin.dto.BlogTransitionRequest;
import dev.bytelore.server.content.admin.dto.CreateBlogPostRequest;
import dev.bytelore.server.content.admin.dto.CreateWhitelistSourceRequest;
import dev.bytelore.server.content.admin.dto.UpdateBlogPostRequest;
import dev.bytelore.server.content.admin.dto.WhitelistSourceFetchResponse;
import dev.bytelore.server.domain.BlogPost;
import dev.bytelore.server.domain.BlogSource;
import dev.bytelore.server.domain.BlogStatus;
import dev.bytelore.server.domain.PipelineAuditLog;
import dev.bytelore.server.domain.PipelineStep;
import dev.bytelore.server.domain.SourceUpdate;
import dev.bytelore.server.domain.VerifyStatus;
import dev.bytelore.server.domain.WhitelistSource;
import dev.bytelore.server.pipeline.PipelineLockService;
import dev.bytelore.server.repository.BlogPostRepository;
import dev.bytelore.server.repository.PipelineAuditLogRepository;
import dev.bytelore.server.repository.SourceUpdateRepository;
import dev.bytelore.server.repository.WhitelistSourceRepository;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The blog ingest pipeline end to end, against a real Postgres Testcontainer and a local fake HTTPS
 * server standing in for a whitelist source's feed and verify endpoints -- see {@link
 * FakeHttpsFeedServer} and {@link TrustFakeServerConfiguration} for why HTTPS, not plain HTTP, is
 * unavoidable here.
 *
 * <p>The rule every test in this class ultimately serves: <strong>an {@code AUTO} post is never
 * published without a human {@code ADMIN} approving it</strong>. {@link
 * #anAutoPostCanNeverBePublishedDirectly_notEvenByAnAdmin()} is the test written specifically to
 * fail if that ever stops being true -- it asserts against the code path ({@code publish} refusing
 * every {@code AUTO} post unconditionally), not merely against one scenario's outcome.
 */
class BlogPipelineIT extends ContentApiTestSupport {

  /**
   * A test-only {@link HttpClient} that trusts any TLS certificate, substituted in place of the
   * production client via {@code @Primary} (see {@code PipelineHttpClientConfig}). Needed only
   * because {@link FakeHttpsFeedServer} presents a throwaway self-signed certificate; nothing about
   * the pipeline's own verification logic is weakened by this, since request-forgery protection
   * lives in URL construction and version validation, not in certificate pinning.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class TrustFakeServerConfiguration {

    @Bean
    @Primary
    HttpClient testPipelineHttpTransport() throws Exception {
      X509ExtendedTrustManager trustAll =
          new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(
                X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkServerTrusted(
                X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkClientTrusted(
                X509Certificate[] chain, String authType, SSLEngine engine) {}

            @Override
            public void checkServerTrusted(
                X509Certificate[] chain, String authType, SSLEngine engine) {}
          };
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new javax.net.ssl.TrustManager[] {trustAll}, new SecureRandom());
      return HttpClient.newBuilder()
          .sslContext(sslContext)
          .connectTimeout(java.time.Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();
    }
  }

  private static final String LONG_ENOUGH_CONTENT =
      "This release includes a number of bug fixes, performance improvements and dependency "
          + "upgrades that are worth reading about in detail.";

  /**
   * A recognisable, obviously-fake token value, configured for every test in this class via {@link
   * #pipelineProperties}. None of this class's fixtures point at a host equal to {@code
   * api.github.com} (they all use {@link FakeHttpsFeedServer}, at {@code 127.0.0.1}), so a
   * configured token here changes no other test's behaviour -- {@code PipelineHttpClient} never
   * attaches it to a request whose host does not match.
   */
  private static final String TEST_GITHUB_TOKEN = "ghp_TestOnlyFixtureToken1234567890abcdefEXAMPLE";

  @DynamicPropertySource
  static void pipelineProperties(DynamicPropertyRegistry registry) {
    registry.add("bytelore.pipeline.github-token", () -> TEST_GITHUB_TOKEN);
  }

  @Autowired private WhitelistSourceRepository whitelistSources;
  @Autowired private SourceUpdateRepository sourceUpdates;
  @Autowired private BlogPostRepository blogPosts;
  @Autowired private PipelineAuditLogRepository auditLogs;
  @Autowired private PipelineLockService lockService;

  private final List<UUID> createdSourceIds = new ArrayList<>();
  private final List<UUID> createdManualPostIds = new ArrayList<>();
  private FakeHttpsFeedServer server;

  @AfterEach
  void cleanup() throws Exception {
    if (server != null) {
      server.close();
    }
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    for (UUID postId : createdManualPostIds) {
      jdbc.update("DELETE FROM pipeline_audit_log WHERE blog_post_id = ?", postId);
      jdbc.update("DELETE FROM blog_posts WHERE id = ?", postId);
    }
    createdManualPostIds.clear();
    for (UUID sourceId : createdSourceIds) {
      jdbc.update("DELETE FROM pipeline_audit_log WHERE whitelist_source_id = ?", sourceId);
      jdbc.update(
          "DELETE FROM pipeline_audit_log WHERE blog_post_id IN "
              + "(SELECT id FROM blog_posts WHERE source_update_id IN "
              + "(SELECT id FROM source_updates WHERE whitelist_source_id = ?))",
          sourceId);
      jdbc.update(
          "DELETE FROM blog_posts WHERE source_update_id IN "
              + "(SELECT id FROM source_updates WHERE whitelist_source_id = ?)",
          sourceId);
      jdbc.update("DELETE FROM source_updates WHERE whitelist_source_id = ?", sourceId);
      jdbc.update("DELETE FROM whitelist_sources WHERE id = ?", sourceId);
    }
    createdSourceIds.clear();
  }

  // ---- Fixtures ---------------------------------------------------------------------------

  private WhitelistSource createSource(String feedUrl, String verifyPattern) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    WhitelistSource source = new WhitelistSource();
    source.setId(UuidV7.randomUuid());
    source.setName("pipeline-it-" + UUID.randomUUID());
    source.setFeedUrl(feedUrl);
    source.setVerifyUrlPattern(verifyPattern);
    source.setEnabled(true);
    source.setCreatedAt(now);
    source.setUpdatedAt(now);
    WhitelistSource saved = whitelistSources.save(source);
    createdSourceIds.add(saved.getId());
    return saved;
  }

  /**
   * A timestamp comfortably inside the {@code ITEM_RECENT} window (§5.7, default {@code
   * max-item-age} of 14 days) no matter which day this suite runs, computed from the wall clock
   * rather than written as a literal -- a fixed literal ages out of the window the moment more than
   * {@code max-item-age} elapses between when it was written and when the suite runs, which is
   * exactly the failure mode {@code #anItemOlderThanTheMaxAgeIsRejectedByItemRecent()} exists to
   * catch on purpose, for a different item.
   */
  private static String recentTimestamp() {
    return Instant.now().minus(java.time.Duration.ofHours(1)).toString();
  }

  private static String atomFeed(String entryId, String title, String link, String content) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Fixture Feed</title>
          <entry>
            <id>%s</id>
            <title>%s</title>
            <link href="%s"/>
            <updated>%s</updated>
            <content type="html">%s</content>
          </entry>
        </feed>
        """
        .formatted(entryId, title, link, recentTimestamp(), content);
  }

  private static String atomFeed(List<String[]> entries) {
    StringBuilder body = new StringBuilder();
    for (String[] entry : entries) {
      body.append(
          """
            <entry>
              <id>%s</id>
              <title>%s</title>
              <link href="%s"/>
              <updated>%s</updated>
              <content type="html">%s</content>
            </entry>
          """
              .formatted(entry[0], entry[1], entry[2], recentTimestamp(), entry[3]));
    }
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Fixture Feed</title>
        %s</feed>
        """
        .formatted(body);
  }

  /**
   * Like {@link #atomFeed(String, String, String, String)}, but with an explicit {@code updated}.
   */
  private static String atomFeedWithTimestamp(
      String entryId, String title, String link, String content, String updated) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Fixture Feed</title>
          <entry>
            <id>%s</id>
            <title>%s</title>
            <link href="%s"/>
            <updated>%s</updated>
            <content type="html">%s</content>
          </entry>
        </feed>
        """
        .formatted(entryId, title, link, updated, content);
  }

  /**
   * Like {@link #atomFeed(String, String, String, String)}, but with no {@code <updated>} at all.
   */
  private static String atomFeedWithoutTimestamp(
      String entryId, String title, String link, String content) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Fixture Feed</title>
          <entry>
            <id>%s</id>
            <title>%s</title>
            <link href="%s"/>
            <content type="html">%s</content>
          </entry>
        </feed>
        """
        .formatted(entryId, title, link, content);
  }

  private WhitelistSourceFetchResponse fetchAs(String token, UUID sourceId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/whitelist-sources/{id}/fetch", sourceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return jsonMapper.readValue(
        result.getResponse().getContentAsString(), WhitelistSourceFetchResponse.class);
  }

  private AdminBlogPostResponse createManualPost(String token, String sourceUrl) throws Exception {
    CreateBlogPostRequest request =
        new CreateBlogPostRequest(
            uniqueSlug("manual-post"), "A Manual Post", LONG_ENOUGH_CONTENT, sourceUrl);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    AdminBlogPostResponse response =
        jsonMapper.readValue(
            result.getResponse().getContentAsString(), AdminBlogPostResponse.class);
    createdManualPostIds.add(response.id());
    return response;
  }

  private void submitPost(String token, UUID postId) throws Exception {
    BlogTransitionRequest submit = new BlogTransitionRequest(BlogStatus.DRAFT, null);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/submit", postId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(submit)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  private List<UUID> reviewQueueOrder(String token, String sort) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/admin/review-queue")
                    .param("size", "100")
                    .param("sort", sort)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<UUID> ids = new ArrayList<>();
    for (JsonNode item : json(result).path("items")) {
      ids.add(UUID.fromString(item.path("id").asString()));
    }
    return ids;
  }

  // ---- Whitelist enforcement -----------------------------------------------------------------

  @Test
  void aDisabledSourceIsNeverFetched_notEvenManually() throws Exception {
    server = FakeHttpsFeedServer.start();
    server.respond(
        "/feed",
        200,
        atomFeed("tag:1", "v9.9.9", server.baseUrl() + "/release", LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "release v9.9.9 is out"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");
    source.setEnabled(false);
    whitelistSources.save(source);

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.fetched()).isZero();
    assertThat(response.created()).isZero();
    assertThat(sourceUpdates.existsByWhitelistSourceId(source.getId())).isFalse();

    List<PipelineAuditLog> log = findByWhitelistSource(source.getId());
    assertThat(log).hasSize(1);
    assertThat(log.get(0).getStep()).isEqualTo(PipelineStep.FETCH);
    assertThat(log.get(0).getReason()).containsIgnoringCase("disabled");
  }

  @Test
  void creatingAWhitelistSourceWithAnInsecureUrlIsRejected() throws Exception {
    CreateWhitelistSourceRequest request =
        new CreateWhitelistSourceRequest(
            "insecure-" + UUID.randomUUID(),
            "http://example.com/feed.atom",
            "https://example.com/verify/{version}",
            true);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/whitelist-sources")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("INSECURE_SOURCE_URL");
  }

  // ---- Dedup --------------------------------------------------------------------------------

  @Test
  void aRepeatOfAnAlreadyProcessedItemIsDroppedNotReprocessed() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/7.2.0";
    server.respond("/feed", 200, atomFeed("tag:dedup", "v7.2.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "release v7.2.0 is out"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse first = fetchAs(adminToken(), source.getId());
    assertThat(first.created()).isEqualTo(1);
    assertThat(first.duplicates()).isZero();

    WhitelistSourceFetchResponse second = fetchAs(adminToken(), source.getId());
    assertThat(second.created()).isZero();
    assertThat(second.duplicates()).isEqualTo(1);

    long sourceUpdateCount =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM source_updates WHERE whitelist_source_id = ?",
                Integer.class,
                source.getId());
    assertThat(sourceUpdateCount).isEqualTo(1);
    long postCount =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM blog_posts WHERE source_update_id IN "
                    + "(SELECT id FROM source_updates WHERE whitelist_source_id = ?)",
                Integer.class,
                source.getId());
    assertThat(postCount).isEqualTo(1);
  }

  // ---- Malformed feed -------------------------------------------------------------------------

  @Test
  void aMalformedFeedIsHandledGracefullyWithoutCrashingTheCycle() throws Exception {
    server = FakeHttpsFeedServer.start();
    server.respond("/feed", 200, "this is not xml at all { garbage </>");

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.fetched()).isZero();
    assertThat(response.created()).isZero();
    assertThat(response.rejected()).isZero();
    List<PipelineAuditLog> log = findByWhitelistSource(source.getId());
    assertThat(log).hasSize(1);
    assertThat(log.get(0).getStep()).isEqualTo(PipelineStep.FETCH);
  }

  // ---- Version confirmation failing ------------------------------------------------------------

  @Test
  void versionConfirmationFailureRejectsTheUpdateAndWritesNoBlogPost() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/3.0.0";
    server.respond("/feed", 200, atomFeed("tag:unverified", "v3.0.0", link, LONG_ENOUGH_CONTENT));
    // The verify endpoint exists but never confirms the claimed version -- a 200 that proves
    // nothing, exactly the case §5.7 calls out as worthless on its own.
    server.respondDynamic(
        "/verify/",
        path -> new FakeHttpsFeedServer.HandlerResult(200, "no matching release found"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.created()).isZero();
    assertThat(response.rejected()).isEqualTo(1);
    assertThat(response.rejections()).hasSize(1);
    assertThat(response.rejections().get(0).failedCheck()).isEqualTo("VERSION_CONFIRMED");

    List<SourceUpdate> stored = findSourceUpdates(source.getId());
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getVerifyStatus()).isEqualTo(VerifyStatus.REJECTED);
    assertThat(stored.get(0).getVerifyChecks()).contains("VERSION_CONFIRMED").contains("false");

    long postCount =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM blog_posts WHERE source_update_id = ?",
                Integer.class,
                stored.get(0).getId());
    assertThat(postCount).isZero();
  }

  // ---- ITEM_RECENT ----------------------------------------------------------------------------

  @Test
  void anItemOlderThanMaxItemAgeIsRejectedWithoutHittingTheNetwork() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/10.0.0";
    String longAgo = Instant.now().minus(Duration.ofDays(30)).toString();
    server.respond(
        "/feed",
        200,
        atomFeedWithTimestamp("tag:old", "v10.0.0", link, LONG_ENOUGH_CONTENT, longAgo));
    AtomicInteger verifyCalls = new AtomicInteger();
    server.respondDynamic(
        "/verify/",
        path -> {
          verifyCalls.incrementAndGet();
          return new FakeHttpsFeedServer.HandlerResult(200, "v10.0.0 is out");
        });

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.created()).isZero();
    assertThat(response.rejected()).isEqualTo(1);
    var rejection = response.rejections().get(0);
    assertThat(rejection.failedCheck()).isEqualTo("ITEM_RECENT");
    // ITEM_RECENT runs before VERSION_CONFIRMED specifically so an old item costs no network
    // request; a call reaching the verify endpoint at all would mean the ordering regressed.
    assertThat(verifyCalls.get()).isZero();

    List<SourceUpdate> stored = findSourceUpdates(source.getId());
    assertThat(stored.get(0).getVerifyChecks()).contains("ITEM_RECENT").contains("false");
  }

  @Test
  void anItemWithNoPublishedTimestampPassesItemRecentByDefault() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/11.0.0";
    server.respond(
        "/feed", 200, atomFeedWithoutTimestamp("tag:notime", "v11.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "v11.0.0 is out"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.created()).isEqualTo(1);
  }

  // ---- STABLE_RELEASE ---------------------------------------------------------------------------

  @Test
  void aPreReleaseVersionIsRejectedByStableReleaseWithoutHittingTheNetwork() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/12.0.0-rc1";
    server.respond("/feed", 200, atomFeed("tag:rc", "v12.0.0-rc1", link, LONG_ENOUGH_CONTENT));
    AtomicInteger verifyCalls = new AtomicInteger();
    server.respondDynamic(
        "/verify/",
        path -> {
          verifyCalls.incrementAndGet();
          return new FakeHttpsFeedServer.HandlerResult(200, "v12.0.0-rc1 is out");
        });

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.created()).isZero();
    assertThat(response.rejected()).isEqualTo(1);
    var rejection = response.rejections().get(0);
    assertThat(rejection.failedCheck()).isEqualTo("STABLE_RELEASE");
    assertThat(verifyCalls.get()).isZero();
  }

  /**
   * The regression case for the extractor's silent-truncation bug: a title with a pre-release
   * suffix directly attached to its numeric core, no separator at all. Before {@link
   * dev.bytelore.server.pipeline.VersionExtractor} was fixed to capture the whole thing, this shape
   * was cut down to {@code "3.15.0"} -- which is a genuine substring of the verify endpoint's real
   * response body below, so a pre-release build could sail through {@code VERSION_CONFIRMED} and be
   * published labeled as the stable release it is only a preview of. Fixed, the full candidate
   * {@code "3.15.0b2"} is extracted, {@code STABLE_RELEASE} catches the attached suffix, and the
   * verify endpoint is never even asked.
   */
  @Test
  void aPreReleaseSuffixWithNoSeparatorIsCaughtNotSilentlyTruncated() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/3.15.0b2";
    server.respond("/feed", 200, atomFeed("tag:adjacent", "3.15.0b2", link, LONG_ENOUGH_CONTENT));
    AtomicInteger verifyCalls = new AtomicInteger();
    server.respondDynamic(
        "/verify/",
        path -> {
          verifyCalls.incrementAndGet();
          return new FakeHttpsFeedServer.HandlerResult(200, "Now shipping 3.15.0b2");
        });

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.created()).isZero();
    assertThat(response.rejected()).isEqualTo(1);
    var rejection = response.rejections().get(0);
    assertThat(rejection.failedCheck()).isEqualTo("STABLE_RELEASE");
    assertThat(rejection.versionString()).isEqualTo("3.15.0b2");
    assertThat(verifyCalls.get()).isZero();
  }

  // ---- Rate-limit deferral (VERSION_CONFIRMED) -------------------------------------------------

  /**
   * The behaviour §5.7 requires of a {@code 403}/{@code 429} from the verify endpoint: the item is
   * deferred, not rejected, so it leaves no {@code SourceUpdate} row and therefore no dedup hash --
   * a later cycle can retry it in full once the rate limit clears, which this test proves by
   * flipping the fake endpoint from rate-limited to confirming and running a second cycle.
   */
  @Test
  void aRateLimitedVerifyResponseDefersTheItemInsteadOfPermanentlyRejectingIt() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/13.0.0";
    server.respond("/feed", 200, atomFeed("tag:ratelimited", "v13.0.0", link, LONG_ENOUGH_CONTENT));
    AtomicBoolean rateLimited = new AtomicBoolean(true);
    server.respondDynamic(
        "/verify/",
        path ->
            rateLimited.get()
                ? new FakeHttpsFeedServer.HandlerResult(403, "API rate limit exceeded")
                : new FakeHttpsFeedServer.HandlerResult(200, "v13.0.0 is out"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse first = fetchAs(adminToken(), source.getId());

    // Not a rejection: excluded from every count, not merely from `rejected`.
    assertThat(first.fetched()).isZero();
    assertThat(first.created()).isZero();
    assertThat(first.duplicates()).isZero();
    assertThat(first.rejected()).isZero();
    assertThat(first.rejections()).isEmpty();
    assertThat(sourceUpdates.existsByWhitelistSourceId(source.getId())).isFalse();

    // Still audited, and distinguishable from an ordinary rejection.
    List<PipelineAuditLog> trail = findByWhitelistSource(source.getId());
    assertThat(trail)
        .anySatisfy(
            row -> {
              assertThat(row.getStep()).isEqualTo(PipelineStep.VERIFY);
              assertThat(row.getReason()).containsIgnoringCase("deferred");
            });

    rateLimited.set(false);
    WhitelistSourceFetchResponse second = fetchAs(adminToken(), source.getId());
    assertThat(second.created()).isEqualTo(1);
    assertThat(second.duplicates()).isZero();
    assertThat(sourceUpdates.existsByWhitelistSourceId(source.getId())).isTrue();
  }

  @Test
  void aTooManyRequestsVerifyResponseAlsoDefersRatherThanRejecting() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/14.0.0";
    server.respond("/feed", 200, atomFeed("tag:429", "v14.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(429, "too many requests"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    assertThat(response.fetched()).isZero();
    assertThat(response.rejected()).isZero();
    assertThat(sourceUpdates.existsByWhitelistSourceId(source.getId())).isFalse();
  }

  // ---- GitHub token secrecy ---------------------------------------------------------------------

  /**
   * The configured GitHub token (see {@link #pipelineProperties}) never reaches a log message, an
   * audit reason, or the fetch response body -- and, since none of this fixture's URLs have host
   * {@code api.github.com}, the fake server never receives it as a header either, proving the
   * host-scoping in {@code PipelineHttpClient} holds over a real request, not only in the pure unit
   * test written directly against it.
   */
  @Test
  void theGithubTokenNeverLeaksToLogsAuditOrTheFetchResponse() throws Exception {
    ch.qos.logback.classic.Logger rootLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    rootLogger.addAppender(appender);
    try {
      server = FakeHttpsFeedServer.start();
      String link = server.baseUrl() + "/release/15.0.0";
      server.respond("/feed", 200, atomFeed("tag:token", "v15.0.0", link, LONG_ENOUGH_CONTENT));
      server.respondDynamic(
          "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "v15.0.0 is out"));

      WhitelistSource source =
          createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/admin/whitelist-sources/{id}/fetch", source.getId())
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      String rawBody = result.getResponse().getContentAsString();
      assertThat(rawBody).doesNotContain(TEST_GITHUB_TOKEN);

      assertThat(server.receivedAuthorizationHeaders()).isEmpty();

      List<String> logMessages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(logMessages).noneMatch(message -> message.contains(TEST_GITHUB_TOKEN));

      List<PipelineAuditLog> auditRows = findByWhitelistSource(source.getId());
      assertThat(auditRows)
          .noneMatch(row -> row.getReason() != null && row.getReason().contains(TEST_GITHUB_TOKEN));
    } finally {
      rootLogger.detachAppender(appender);
    }
  }

  // ---- Happy path -----------------------------------------------------------------------------

  /**
   * Feed content is the one input that really is HTML, so the pipeline still transforms it: the
   * drafted excerpt keeps the markup the allow-list admits and loses everything else, including a
   * script tag and a tracking attribute. This is the counterpart to the authored-content rule --
   * there the boundary refuses rather than rewrites, here it rewrites, and the drafted body then
   * has to satisfy the very check an authored body faces, or the pipeline could never store it.
   */
  @Test
  void feedHtmlIsStillTransformedAndTheDangerousPartIsDropped() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/6.0.0";
    String feedHtml =
        "&lt;h2&gt;What is new&lt;/h2&gt;"
            + "&lt;p&gt;A &lt;strong&gt;big&lt;/strong&gt; release with "
            + "&lt;a href=\"https://example.test/pr/1\" data-octo-click=\"track\"&gt;one fix&lt;/a&gt; "
            + "and plenty of detail worth reading about in full.&lt;/p&gt;"
            + "&lt;script&gt;alert(1)&lt;/script&gt;"
            + "&lt;img src=x onerror=\"alert(2)\"&gt;";
    server.respond("/feed", 200, atomFeed("tag:sanitized", "v6.0.0", link, feedHtml));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "Now shipping v6.0.0!"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    assertThat(response.created()).isEqualTo(1);

    BlogPost draft = findPostBySourceUpdateId(response.createdSourceUpdateIds().get(0));
    String body = draft.getBodyMarkdown();

    assertThat(body)
        .doesNotContain("<script")
        .doesNotContain("onerror")
        .doesNotContain("data-octo-click");
    assertThat(body).contains("<h2>What is new</h2>").contains("<strong>big</strong>");
    // The mandatory source citation survives whatever the excerpt did.
    assertThat(body).contains("[View the original announcement](" + link + ")");
  }

  /**
   * A feed item whose drafted body would not clear the write boundary is rejected as a verification
   * failure, and only that item is lost.
   *
   * <p>The scenario is not contrived: a security advisory that quotes a {@code javascript:} link in
   * markdown link syntax survives the excerpt transformer as ordinary text -- there is no markup to
   * strip -- and is then refused once it sits inside a markdown body, because a renderer would turn
   * it into a live anchor. If that refusal escaped as an exception it would roll back the item's
   * own source-update row and audit entry, abandon every later item in the feed, and leave the
   * source's fetch timestamp unset, so the next cycle would stall in the same place with nothing
   * recorded to explain it. The assertions are exactly those four properties.
   */
  @Test
  void anItemWhoseDraftWouldBeRefusedIsRejectedWithoutEndingTheCycle() throws Exception {
    server = FakeHttpsFeedServer.start();
    String badLink = server.baseUrl() + "/release/7.0.0";
    String goodLink = server.baseUrl() + "/release/7.0.1";
    server.respond(
        "/feed",
        200,
        atomFeed(
            List.of(
                new String[] {
                  "tag:bad",
                  "v7.0.0",
                  badLink,
                  "A security fix. Do not click [click me](javascript:alert(1)) anywhere, and read "
                      + "the rest of these notes carefully before upgrading anything at all."
                },
                new String[] {"tag:good", "v7.0.1", goodLink, LONG_ENOUGH_CONTENT})));
    server.respondDynamic(
        "/verify/",
        path -> new FakeHttpsFeedServer.HandlerResult(200, "Now shipping v7.0.0 v7.0.1"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());

    // The bad item is rejected by name, the good one still becomes a draft.
    assertThat(response.rejected()).isEqualTo(1);
    assertThat(response.rejections())
        .singleElement()
        .satisfies(
            rejection -> {
              assertThat(rejection.failedCheck()).isEqualTo("DRAFT_VALIDATION");
              assertThat(rejection.detail()).contains("scheme");
            });
    assertThat(response.created()).isEqualTo(1);

    // The rejected item left a durable, inspectable trace rather than vanishing: a REJECTED source
    // update carrying the failed check, and a VERIFY audit entry naming it.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String checks =
        jdbc.queryForObject(
            "SELECT verify_checks FROM source_updates "
                + "WHERE whitelist_source_id = ? AND verify_status = 'REJECTED'",
            String.class,
            source.getId());
    assertThat(checks).contains("DRAFT_VALIDATION").contains("false");
    assertThat(findByWhitelistSource(source.getId()))
        .anySatisfy(
            row -> {
              assertThat(row.getStep()).isEqualTo(PipelineStep.VERIFY);
              assertThat(row.getReason()).contains("DRAFT_VALIDATION");
            });

    // The cycle completed, so the source is not stuck repeating the same failing item forever.
    assertThat(whitelistSources.findById(source.getId()).orElseThrow().getLastFetchedAt())
        .isNotNull();
  }

  @Test
  void theHappyPathReachesPublishedWithACompleteAuditTrail() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/5.1.0";
    server.respond("/feed", 200, atomFeed("tag:happy", "v5.1.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "Now shipping v5.1.0!"));

    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    UUID adminUserId = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    assertThat(response.created()).isEqualTo(1);
    UUID sourceUpdateId = response.createdSourceUpdateIds().get(0);

    BlogPost draft = findPostBySourceUpdateId(sourceUpdateId);
    assertThat(draft.getStatus()).isEqualTo(BlogStatus.PENDING_REVIEW);
    assertThat(draft.getSource()).isEqualTo(BlogSource.AUTO);
    assertThat(draft.getSourceUrl()).isEqualTo(link);

    // This run was triggered manually by an administrator, so -- unlike a scheduled run -- every
    // step it produced is attributable to that administrator (§5.7: "a manual run is
    // attributable, a scheduled one is not").
    List<PipelineAuditLog> postTrail =
        auditLogs.findByBlogPostIdOrderByOccurredAtAsc(draft.getId());
    assertThat(postTrail)
        .extracting(PipelineAuditLog::getStep)
        .containsExactly(PipelineStep.DRAFT, PipelineStep.SUBMIT);
    assertThat(postTrail)
        .allSatisfy(row -> assertThat(row.getActorUserId()).isEqualTo(adminUserId));

    List<PipelineAuditLog> sourceTrail = findByWhitelistSource(source.getId());
    assertThat(sourceTrail)
        .extracting(PipelineAuditLog::getStep)
        .containsExactly(PipelineStep.FETCH, PipelineStep.VERIFY);
    assertThat(sourceTrail)
        .allSatisfy(row -> assertThat(row.getActorUserId()).isEqualTo(adminUserId));

    // Approve as ADMIN.
    BlogTransitionRequest approve =
        new BlogTransitionRequest(BlogStatus.PENDING_REVIEW, "Confirmed against the release tag.");
    MvcResult approveResult =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/approve", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(approve)))
            .andReturn();
    assertThat(approveResult.getResponse().getStatus()).isEqualTo(200);
    AdminBlogPostResponse published =
        jsonMapper.readValue(
            approveResult.getResponse().getContentAsString(), AdminBlogPostResponse.class);
    assertThat(published.status()).isEqualTo(BlogStatus.PUBLISHED);
    assertThat(published.publishedAt()).isNotNull();

    List<PipelineAuditLog> finalTrail =
        auditLogs.findByBlogPostIdOrderByOccurredAtAsc(draft.getId());
    assertThat(finalTrail)
        .extracting(PipelineAuditLog::getStep)
        .containsExactly(PipelineStep.DRAFT, PipelineStep.SUBMIT, PipelineStep.APPROVE);
    PipelineAuditLog approveRow = finalTrail.get(2);
    assertThat(approveRow.getActorUserId()).isNotNull();
    assertThat(approveRow.getFromStatus()).isEqualTo(BlogStatus.PENDING_REVIEW);
    assertThat(approveRow.getToStatus()).isEqualTo(BlogStatus.PUBLISHED);
  }

  // ---- The invariant this phase exists to enforce ----------------------------------------------

  /**
   * There is no code path -- no flag, no parameter, no admin convenience -- that publishes an
   * {@code AUTO} post without going through {@code approve}. This asserts against {@code publish}
   * itself refusing every {@code AUTO} post unconditionally, as an {@code ADMIN} caller, which is
   * the highest privilege this API has: if this ever passes for an {@code ADMIN}, the invariant is
   * gone regardless of what any other test shows.
   */
  @Test
  void anAutoPostCanNeverBePublishedDirectly_notEvenByAnAdmin() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/1.0.0";
    server.respond("/feed", 200, atomFeed("tag:noskip", "v1.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/",
        path -> new FakeHttpsFeedServer.HandlerResult(200, "1.0.0 is now available (v1.0.0)"));
    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    BlogPost draft = findPostBySourceUpdateId(response.createdSourceUpdateIds().get(0));
    assertThat(draft.getStatus()).isEqualTo(BlogStatus.PENDING_REVIEW);

    BlogTransitionRequest publishAttempt =
        new BlogTransitionRequest(BlogStatus.PENDING_REVIEW, "Attempting to skip review.");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/publish", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(publishAttempt)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(result)).isEqualTo("AUTO_POST_APPROVAL_REQUIRED");

    BlogPost stillPending = blogPosts.findById(draft.getId()).orElseThrow();
    assertThat(stillPending.getStatus()).isEqualTo(BlogStatus.PENDING_REVIEW);
    assertThat(stillPending.getPublishedAt()).isNull();
  }

  @Test
  void anEditorMayReadTheReviewQueueButMayNotApprove() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/2.0.0";
    server.respond("/feed", 200, atomFeed("tag:editor", "v2.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "v2.0.0 shipped"));
    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");
    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    BlogPost draft = findPostBySourceUpdateId(response.createdSourceUpdateIds().get(0));

    String editorToken = editorToken();

    MvcResult queueRead =
        mockMvc
            .perform(
                get("/api/v1/admin/review-queue")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken))
            .andReturn();
    assertThat(queueRead.getResponse().getStatus()).isEqualTo(200);

    BlogTransitionRequest approve =
        new BlogTransitionRequest(BlogStatus.PENDING_REVIEW, "Editor trying to approve.");
    MvcResult approveAttempt =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/approve", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(approve)))
            .andReturn();
    assertThat(approveAttempt.getResponse().getStatus()).isEqualTo(403);

    BlogPost stillPending = blogPosts.findById(draft.getId()).orElseThrow();
    assertThat(stillPending.getStatus()).isEqualTo(BlogStatus.PENDING_REVIEW);
  }

  @Test
  void anEditorMayNotEditTheBodyOfAnAutoPostButAnAdminMay() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/6.0.0";
    server.respond("/feed", 200, atomFeed("tag:editbody", "v6.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "v6.0.0 is live"));
    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");
    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    BlogPost draft = findPostBySourceUpdateId(response.createdSourceUpdateIds().get(0));

    String body = "{\"title\":\"Rewritten\",\"version\":%d}".formatted(draft.getVersion());
    MvcResult editorAttempt =
        mockMvc
            .perform(
                patch("/api/v1/admin/blog/posts/{id}", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
    assertThat(editorAttempt.getResponse().getStatus()).isEqualTo(403);
    assertThat(errorCode(editorAttempt)).isEqualTo("AUTO_POST_NOT_EDITABLE");

    MvcResult adminAttempt =
        mockMvc
            .perform(
                patch("/api/v1/admin/blog/posts/{id}", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
    assertThat(adminAttempt.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void theSourceLinkOfAnAutoPostIsImmutableForEveryRoleIncludingAdmin() throws Exception {
    server = FakeHttpsFeedServer.start();
    String link = server.baseUrl() + "/release/8.0.0";
    server.respond("/feed", 200, atomFeed("tag:immutable", "v8.0.0", link, LONG_ENOUGH_CONTENT));
    server.respondDynamic(
        "/verify/", path -> new FakeHttpsFeedServer.HandlerResult(200, "v8.0.0 released"));
    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");
    WhitelistSourceFetchResponse response = fetchAs(adminToken(), source.getId());
    BlogPost draft = findPostBySourceUpdateId(response.createdSourceUpdateIds().get(0));

    String body =
        "{\"source_url\":\"https://example.com/somewhere-else\",\"version\":%d}"
            .formatted(draft.getVersion());
    MvcResult result =
        mockMvc
            .perform(
                patch("/api/v1/admin/blog/posts/{id}", draft.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(errorCode(result)).isEqualTo("AUTO_POST_NOT_EDITABLE");
  }

  // ---- Manual trigger vs. a held lock -----------------------------------------------------------

  @Test
  void aManualFetchCollidingWithAHeldLockAnswers409() throws Exception {
    server = FakeHttpsFeedServer.start();
    server.respond(
        "/feed",
        200,
        atomFeed("tag:locked", "v1.1.1", server.baseUrl() + "/r", LONG_ENOUGH_CONTENT));
    WhitelistSource source =
        createSource(server.baseUrl() + "/feed", server.baseUrl() + "/verify/{version}");

    Optional<SimpleLock> held = lockService.tryLock(source.getId());
    assertThat(held).isPresent();
    try {
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/admin/whitelist-sources/{id}/fetch", source.getId())
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(409);
      assertThat(errorCode(result)).isEqualTo("PIPELINE_RUN_IN_PROGRESS");
    } finally {
      held.get().unlock();
    }
  }

  // ---- Review queue sort whitelist -------------------------------------------------------------

  /**
   * The direct case the sort whitelist exists for: requesting {@code created_at,desc} over real
   * HTTP has to genuinely reverse the page relative to {@code created_at,asc}, not merely be
   * accepted with the direction silently dropped. {@code updated_at} -- the other documented field
   * -- is checked too, unqualified, so both whitelist entries are exercised end to end.
   */
  @Test
  void reviewQueueSortAcceptsBothFieldsAndAnExplicitDirectionReversesTheOrder() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse first = createManualPost(token, null);
    submitPost(token, first.id());
    Thread.sleep(5);
    AdminBlogPostResponse second = createManualPost(token, null);
    submitPost(token, second.id());

    List<UUID> ascending = reviewQueueOrder(token, "created_at,asc");
    assertThat(ascending.indexOf(first.id())).isLessThan(ascending.indexOf(second.id()));

    List<UUID> descending = reviewQueueOrder(token, "created_at,desc");
    assertThat(descending.indexOf(second.id())).isLessThan(descending.indexOf(first.id()));

    List<UUID> byUpdatedAt = reviewQueueOrder(token, "updated_at");
    assertThat(byUpdatedAt).contains(first.id(), second.id());
  }

  @Test
  void reviewQueueRejectsAnUnsupportedSortField() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/admin/review-queue")
                    .param("sort", "bogus")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("INVALID_SORT_FIELD");
  }

  // ---- Manual post source_url validation --------------------------------------------------------

  @Test
  void manualPostSourceUrlAcceptsAnAbsoluteHttpsLinkOnCreate() throws Exception {
    AdminBlogPostResponse created =
        createManualPost(adminToken(), "https://example.com/announcement");
    assertThat(created.sourceUrl()).isEqualTo("https://example.com/announcement");
  }

  @Test
  void manualPostSourceUrlAcceptsNullOrBlankOnCreate() throws Exception {
    AdminBlogPostResponse withNull = createManualPost(adminToken(), null);
    assertThat(withNull.sourceUrl()).isNull();
  }

  @Test
  void manualPostSourceUrlRejectsAPlainHttpLinkOnCreate() throws Exception {
    CreateBlogPostRequest request =
        new CreateBlogPostRequest(
            uniqueSlug("manual-post"),
            "A Manual Post",
            LONG_ENOUGH_CONTENT,
            "http://example.com/announcement");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("INSECURE_SOURCE_URL");
  }

  @Test
  void manualPostSourceUrlRejectsAJavascriptLinkOnCreate() throws Exception {
    CreateBlogPostRequest request =
        new CreateBlogPostRequest(
            uniqueSlug("manual-post"), "A Manual Post", LONG_ENOUGH_CONTENT, "javascript:alert(1)");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("INSECURE_SOURCE_URL");
  }

  @Test
  void manualPostSourceUrlRejectsANonHttpsLinkOnUpdate() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse created = createManualPost(token, null);

    UpdateBlogPostRequest update =
        new UpdateBlogPostRequest(
            null, null, null, "http://example.com/somewhere", created.version());
    MvcResult result =
        mockMvc
            .perform(
                patch("/api/v1/admin/blog/posts/{id}", created.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(update)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("INSECURE_SOURCE_URL");
  }

  // ---- Transition reason lower bound
  // -------------------------------------------------------------

  @Test
  void transitionReasonAtLeastTenCharactersIsAcceptedOnAnOptionalTransition() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse created = createManualPost(token, null);

    BlogTransitionRequest submit =
        new BlogTransitionRequest(BlogStatus.DRAFT, "Ten chars or more, exactly enough.");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/submit", created.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(submit)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void transitionReasonUnderTenCharactersIsRejectedEvenOnAnOptionalTransition() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse created = createManualPost(token, null);

    BlogTransitionRequest submit = new BlogTransitionRequest(BlogStatus.DRAFT, "too short");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/submit", created.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(submit)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");

    BlogPost stillDraft = blogPosts.findById(created.id()).orElseThrow();
    assertThat(stillDraft.getStatus()).isEqualTo(BlogStatus.DRAFT);
  }

  @Test
  void transitionReasonIsStillOptionalOnATransitionThatDoesNotRequireOne() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse created = createManualPost(token, null);

    BlogTransitionRequest submit = new BlogTransitionRequest(BlogStatus.DRAFT, null);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/blog/posts/{id}/submit", created.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(submit)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  // ---- Admin list filtering (q/status/source) ------------------------------------------------

  /**
   * The escaped case: {@code GET /admin/blog/posts} with no {@code q} at all, exactly the request
   * the admin screen makes by default. Before the fix this 500'd against a live server ({@code
   * function lower(bytea) does not exist}) because the JPQL parameter bound for the title filter
   * was a null {@code String} passed straight into {@code LOWER(CONCAT(...))}; every one of the 104
   * integration tests that existed before this class change only ever exercised {@code
   * /admin/review-queue}, never this endpoint without {@code q} -- see {@link
   * #reviewQueueOrder(String, String)} versus this test's direct hit on {@code /admin/blog/posts}.
   */
  @Test
  void adminListWithoutQuerySucceeds() throws Exception {
    String token = adminToken();
    AdminBlogPostResponse created = createManualPost(token, null);

    MvcResult result = listPosts(token, null, null, null);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(idsOf(result)).contains(created.id());
  }

  @Test
  void adminListFiltersByTitleCaseInsensitiveSubstring() throws Exception {
    String token = adminToken();
    String marker = "zx" + UUID.randomUUID().toString().substring(0, 8);
    BlogPost matching =
        saveDirectPost(BlogStatus.DRAFT, BlogSource.MANUAL, "A post about " + marker + " widgets");
    saveDirectPost(BlogStatus.DRAFT, BlogSource.MANUAL, "A post about something else entirely");

    MvcResult result = listPosts(token, marker.toUpperCase(java.util.Locale.ROOT), null, null);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(titlesOf(result)).containsExactly(matching.getTitle());
  }

  @Test
  void adminListCombinesQueryStatusAndSourceIndependently() throws Exception {
    String token = adminToken();
    String marker = "combo" + UUID.randomUUID().toString().substring(0, 8);
    BlogPost target = saveDirectPost(BlogStatus.DRAFT, BlogSource.MANUAL, marker + " alpha");
    saveDirectPost(
        BlogStatus.DRAFT, BlogSource.AUTO, marker + " beta", createVerifiedSourceUpdate());
    saveDirectPost(BlogStatus.PENDING_REVIEW, BlogSource.MANUAL, marker + " gamma");
    saveDirectPost(BlogStatus.DRAFT, BlogSource.MANUAL, "unrelated " + UUID.randomUUID());

    MvcResult result = listPosts(token, marker, "DRAFT", "MANUAL");

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(titlesOf(result)).containsExactly(target.getTitle());
  }

  @Test
  void adminListWithNonMatchingQueryReturnsEmptyPage() throws Exception {
    String token = adminToken();
    saveDirectPost(BlogStatus.DRAFT, BlogSource.MANUAL, "Some Title " + UUID.randomUUID());

    MvcResult result = listPosts(token, "no-such-title-" + UUID.randomUUID(), null, null);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(titlesOf(result)).isEmpty();
    assertThat(json(result).path("total_elements").asLong()).isZero();
  }

  // ---- helpers -------------------------------------------------------------------------------

  /**
   * Saves a {@link BlogPost} straight through the repository, bypassing the API's own validation,
   * so a test can set up an arbitrary {@code status}/{@code source}/{@code title} combination (e.g.
   * an {@code AUTO} post, or one already in {@code PENDING_REVIEW}) without driving it through the
   * whole pipeline or the transition endpoints just to test the list filter.
   */
  private BlogPost saveDirectPost(BlogStatus status, BlogSource source, String title) {
    return saveDirectPost(status, source, title, null);
  }

  /**
   * Overload for an {@code AUTO} post: {@code ck_blog_posts_source_update_pairing} requires a
   * non-null {@code source_update_id} exactly when {@code source = 'AUTO'}, so such a fixture needs
   * a real {@link SourceUpdate} row (see {@link #createVerifiedSourceUpdate()}) rather than the
   * {@code null} every {@code MANUAL} fixture uses.
   */
  private BlogPost saveDirectPost(
      BlogStatus status, BlogSource source, String title, UUID sourceUpdateId) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    BlogPost post = new BlogPost();
    post.setId(UuidV7.randomUuid());
    post.setSlug(uniqueSlug("direct-post"));
    post.setTitle(title);
    post.setBodyMarkdown(LONG_ENOUGH_CONTENT);
    post.setStatus(status);
    post.setSource(source);
    post.setSourceUrl(source == BlogSource.AUTO ? "https://example.test/release" : null);
    post.setSourceUpdateId(sourceUpdateId);
    post.setCreatedBy(null);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    BlogPost saved = blogPosts.save(post);
    createdManualPostIds.add(saved.getId());
    return saved;
  }

  /**
   * A minimal, already-{@code VERIFIED} {@link SourceUpdate} (and the {@link WhitelistSource} it
   * must belong to) for building an {@code AUTO} {@link BlogPost} fixture directly, without driving
   * a fetch through {@link FakeHttpsFeedServer}. Cleaned up by the existing {@code createSourceIds}
   * teardown in {@link #cleanup()}, which already deletes any {@code blog_posts} row pointing at a
   * tracked whitelist source's updates.
   */
  private UUID createVerifiedSourceUpdate() {
    WhitelistSource source =
        createSource(
            "https://example.test/feed-" + UUID.randomUUID(),
            "https://example.test/verify/{version}");
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    SourceUpdate update = new SourceUpdate();
    update.setId(UuidV7.randomUuid());
    update.setWhitelistSourceId(source.getId());
    update.setRawContent(LONG_ENOUGH_CONTENT);
    update.setVersionString("v1.0.0");
    // ck_source_updates_content_hash requires a 64-character lowercase hex string (a sha256
    // digest); two UUIDs with their dashes stripped give 64 hex characters without pulling in a
    // real digest just for a fixture that is never verified against its content.
    update.setContentHash(
        (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", ""));
    update.setFetchedAt(now);
    update.setVerifyStatus(VerifyStatus.VERIFIED);
    update.setVerifyChecks("[]");
    update.setCreatedAt(now);
    return sourceUpdates.save(update).getId();
  }

  private MvcResult listPosts(String token, String q, String status, String source)
      throws Exception {
    var request =
        get("/api/v1/admin/blog/posts")
            .param("page", "0")
            .param("size", "20")
            .param("sort", "created_at,desc")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (q != null) {
      request = request.param("q", q);
    }
    if (status != null) {
      request = request.param("status", status);
    }
    if (source != null) {
      request = request.param("source", source);
    }
    return mockMvc.perform(request).andReturn();
  }

  private List<String> titlesOf(MvcResult result) throws Exception {
    List<String> titles = new ArrayList<>();
    for (JsonNode item : json(result).path("items")) {
      titles.add(item.path("title").asString());
    }
    return titles;
  }

  private List<UUID> idsOf(MvcResult result) throws Exception {
    List<UUID> ids = new ArrayList<>();
    for (JsonNode item : json(result).path("items")) {
      ids.add(UUID.fromString(item.path("id").asString()));
    }
    return ids;
  }

  private List<PipelineAuditLog> findByWhitelistSource(UUID whitelistSourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM pipeline_audit_log WHERE whitelist_source_id = ? ORDER BY occurred_at ASC",
            UUID.class,
            whitelistSourceId);
    List<PipelineAuditLog> rows = new ArrayList<>();
    for (UUID id : ids) {
      auditLogs.findById(id).ifPresent(rows::add);
    }
    return rows;
  }

  private List<SourceUpdate> findSourceUpdates(UUID whitelistSourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM source_updates WHERE whitelist_source_id = ?",
            UUID.class,
            whitelistSourceId);
    List<SourceUpdate> rows = new ArrayList<>();
    for (UUID id : ids) {
      sourceUpdates.findById(id).ifPresent(rows::add);
    }
    return rows;
  }

  private BlogPost findPostBySourceUpdateId(UUID sourceUpdateId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID postId =
        jdbc.queryForObject(
            "SELECT id FROM blog_posts WHERE source_update_id = ?", UUID.class, sourceUpdateId);
    return blogPosts.findById(postId).orElseThrow();
  }
}
