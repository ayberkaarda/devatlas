package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.admin.dto.AdminBlogPostResponse;
import dev.devatlas.server.content.admin.dto.BlogTransitionRequest;
import dev.devatlas.server.content.admin.dto.CreateWhitelistSourceRequest;
import dev.devatlas.server.content.admin.dto.WhitelistSourceFetchResponse;
import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.BlogSource;
import dev.devatlas.server.domain.BlogStatus;
import dev.devatlas.server.domain.PipelineAuditLog;
import dev.devatlas.server.domain.PipelineStep;
import dev.devatlas.server.domain.SourceUpdate;
import dev.devatlas.server.domain.VerifyStatus;
import dev.devatlas.server.domain.WhitelistSource;
import dev.devatlas.server.pipeline.PipelineLockService;
import dev.devatlas.server.repository.BlogPostRepository;
import dev.devatlas.server.repository.PipelineAuditLogRepository;
import dev.devatlas.server.repository.SourceUpdateRepository;
import dev.devatlas.server.repository.WhitelistSourceRepository;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

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

  @Autowired private WhitelistSourceRepository whitelistSources;
  @Autowired private SourceUpdateRepository sourceUpdates;
  @Autowired private BlogPostRepository blogPosts;
  @Autowired private PipelineAuditLogRepository auditLogs;
  @Autowired private PipelineLockService lockService;

  private final List<UUID> createdSourceIds = new ArrayList<>();
  private FakeHttpsFeedServer server;

  @AfterEach
  void cleanup() throws Exception {
    if (server != null) {
      server.close();
    }
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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

  private static String atomFeed(String entryId, String title, String link, String content) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Fixture Feed</title>
          <entry>
            <id>%s</id>
            <title>%s</title>
            <link href="%s"/>
            <updated>2026-01-01T00:00:00Z</updated>
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

  // ---- Happy path -----------------------------------------------------------------------------

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

  // ---- helpers -------------------------------------------------------------------------------

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
