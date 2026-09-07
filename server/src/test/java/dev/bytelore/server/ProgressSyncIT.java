package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.bytelore.server.auth.JwtService;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.domain.Difficulty;
import dev.bytelore.server.domain.Lesson;
import dev.bytelore.server.domain.Module;
import dev.bytelore.server.domain.Track;
import dev.bytelore.server.ratelimit.RateLimitProperties;
import dev.bytelore.server.repository.LessonRepository;
import dev.bytelore.server.repository.ModuleRepository;
import dev.bytelore.server.repository.TrackRepository;
import dev.bytelore.server.sync.dto.ProgressSyncItem;
import dev.bytelore.server.sync.dto.ProgressSyncRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Both directions of progress sync (§5.8 of the REST contract) against a real Postgres
 * Testcontainer.
 *
 * <p>Assertions are written against the contract -- the reconciliation rules, the per-item result
 * shape and the error catalogue -- rather than against the current shape of the service classes.
 * The two properties this suite cares about most are the ones a client cannot recover from if the
 * server gets them wrong: that a replayed batch leaves the database byte for byte as it was, and
 * that no request shape lets one person read or overwrite another's rows.
 *
 * <p>Timestamps are chosen relative to real wall-clock time rather than through a fake clock. The
 * clamp threshold is a full day, so "48 hours from now" is unambiguously past it and "two hours
 * ago" is unambiguously inside it, whatever the machine's clock happens to read -- and that keeps
 * this class inside the Spring context the other content suites share instead of standing up a
 * second container for a clock it does not need.
 */
class ProgressSyncIT extends ContentApiTestSupport {

  private static final String PROGRESS_PATH = "/api/v1/sync/progress";

  @Autowired private TrackRepository tracks;
  @Autowired private ModuleRepository modules;
  @Autowired private LessonRepository lessons;
  @Autowired private JwtService jwt;
  @Autowired private RateLimitProperties rateLimits;

  private UUID trackId;
  private UUID moduleId;
  private String token;
  private UUID userId;
  private Instant testStart;
  private int lessonCount;

  /** Every account this class signs in as, so their progress rows can be removed afterwards. */
  private final Set<UUID> touchedUserIds = new HashSet<>();

  private int originalSyncBudget;

  @BeforeEach
  void createFixtures() throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    testStart = now;
    lessonCount = 0;

    Track track = new Track();
    track.setId(UuidV7.randomUuid());
    track.setSlug(uniqueSlug("progress-sync-it"));
    track.setTitle("Progress sync fixture track");
    track.setDisplayOrder(1);
    track.setPublished(true);
    track.setContentVersion(1);
    track.setCreatedAt(now);
    track.setUpdatedAt(now);
    trackId = tracks.saveAndFlush(track).getId();

    Module module = new Module();
    module.setId(UuidV7.randomUuid());
    module.setTrackId(trackId);
    module.setTitle("Progress sync fixture module");
    module.setDisplayOrder(1);
    module.setCreatedAt(now);
    module.setUpdatedAt(now);
    moduleId = modules.saveAndFlush(module).getId();

    originalSyncBudget = rateLimits.getSyncProgressPerHour();
    token = newUserToken();
    userId = jwt.verify(token).userId();
  }

  @AfterEach
  void removeFixtures() {
    rateLimits.setSyncProgressPerHour(originalSyncBudget);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    for (UUID id : touchedUserIds) {
      jdbc.update("DELETE FROM user_progress WHERE user_id = ?", id);
      jdbc.update(
          "DELETE FROM rate_limit_counters WHERE bucket_key LIKE ?", "sync-progress-%" + id);
    }
    touchedUserIds.clear();
    deleteTrackTree(trackId);
  }

  // -------------------------------------------------------------------------------------------
  // Reconciliation
  // -------------------------------------------------------------------------------------------

  @Test
  void anUnseenLessonIsInsertedAndReportedApplied() throws Exception {
    UUID lessonId = newLesson();
    Instant completedAt = hoursAgo(3);
    Instant clientUpdatedAt = hoursAgo(2);

    JsonNode body = json(push(token, item(lessonId, completedAt, clientUpdatedAt)));

    assertThat(body.path("applied_count").asInt()).isEqualTo(1);
    assertThat(body.path("stale_count").asInt()).isZero();
    assertThat(body.path("rejected_count").asInt()).isZero();
    assertThat(body.path("clamped_count").asInt()).isZero();
    assertThat(body.path("server_time").asString()).isNotBlank();

    JsonNode result = body.path("results").get(0);
    assertThat(result.path("lesson_id").asString()).isEqualTo(lessonId.toString());
    assertThat(result.path("status").asString()).isEqualTo("APPLIED");
    assertThat(result.path("code").isNull()).isTrue();
    assertThat(result.path("clamped").asBoolean()).isFalse();
    assertThat(instant(result.path("server_client_updated_at"))).isEqualTo(clientUpdatedAt);

    JsonNode stored = onlyPulledRow(token);
    assertThat(instant(stored.path("completed_at"))).isEqualTo(completedAt);
    assertThat(instant(stored.path("client_updated_at"))).isEqualTo(clientUpdatedAt);
  }

  @Test
  void aStrictlyNewerItemOverwritesTheStoredRow() throws Exception {
    UUID lessonId = newLesson();
    push(token, item(lessonId, hoursAgo(5), hoursAgo(5)));

    Instant newerCompletedAt = hoursAgo(1);
    Instant newerClientUpdatedAt = hoursAgo(1);
    JsonNode body = json(push(token, item(lessonId, newerCompletedAt, newerClientUpdatedAt)));

    assertThat(body.path("applied_count").asInt()).isEqualTo(1);
    assertThat(body.path("results").get(0).path("status").asString()).isEqualTo("APPLIED");

    JsonNode stored = onlyPulledRow(token);
    assertThat(instant(stored.path("completed_at"))).isEqualTo(newerCompletedAt);
    assertThat(instant(stored.path("client_updated_at"))).isEqualTo(newerClientUpdatedAt);
  }

  /**
   * Equal timestamps keep the stored row. Without this the rule would not be deterministic: two
   * devices holding the same value would each overwrite the other on every sync, forever.
   */
  @Test
  void anItemWithAnEqualTimestampIsStaleAndTheStoredRowStands() throws Exception {
    UUID lessonId = newLesson();
    Instant clientUpdatedAt = hoursAgo(4);
    Instant firstCompletedAt = hoursAgo(4);
    push(token, item(lessonId, firstCompletedAt, clientUpdatedAt));

    JsonNode body = json(push(token, item(lessonId, hoursAgo(1), clientUpdatedAt)));

    assertThat(body.path("stale_count").asInt()).isEqualTo(1);
    assertThat(body.path("applied_count").asInt()).isZero();
    JsonNode result = body.path("results").get(0);
    assertThat(result.path("status").asString()).isEqualTo("STALE");
    assertThat(result.path("code").isNull()).isTrue();
    assertThat(instant(result.path("server_client_updated_at"))).isEqualTo(clientUpdatedAt);

    // The competing completed_at was discarded with the item, not merged into the winning row.
    assertThat(instant(onlyPulledRow(token).path("completed_at"))).isEqualTo(firstCompletedAt);
  }

  @Test
  void anOlderItemIsStale() throws Exception {
    UUID lessonId = newLesson();
    Instant winner = hoursAgo(1);
    push(token, item(lessonId, winner, winner));

    JsonNode body = json(push(token, item(lessonId, hoursAgo(9), hoursAgo(9))));

    assertThat(body.path("stale_count").asInt()).isEqualTo(1);
    assertThat(instant(body.path("results").get(0).path("server_client_updated_at")))
        .isEqualTo(winner);
    assertThat(instant(onlyPulledRow(token).path("completed_at"))).isEqualTo(winner);
  }

  /**
   * A device with a dead clock battery is not misbehaving, so its work is accepted and only the
   * impossible part of the metadata is discarded. The value actually stored comes back in the same
   * response, which is what lets the device converge on its next sync rather than deadlocking.
   */
  @Test
  void aFarFutureClientTimestampIsClampedToServerTimeAndReportedBack() throws Exception {
    UUID lessonId = newLesson();
    Instant farFuture = Instant.now().plus(Duration.ofHours(48)).truncatedTo(ChronoUnit.MILLIS);

    JsonNode body = json(push(token, item(lessonId, hoursAgo(2), farFuture)));

    assertThat(body.path("clamped_count").asInt()).isEqualTo(1);
    assertThat(body.path("applied_count").asInt()).isEqualTo(1);

    JsonNode result = body.path("results").get(0);
    assertThat(result.path("clamped").asBoolean()).isTrue();
    Instant serverTime = instant(body.path("server_time"));
    assertThat(instant(result.path("server_client_updated_at"))).isEqualTo(serverTime);

    // Stored, not merely reported: a clamped value that never reached the row would win every
    // future comparison exactly as the unclamped one would have.
    assertThat(instant(onlyPulledRow(token).path("client_updated_at"))).isEqualTo(serverTime);
  }

  @Test
  void aFarFutureCompletedAtIsClampedTheSameWay() throws Exception {
    UUID lessonId = newLesson();
    Instant farFuture = Instant.now().plus(Duration.ofDays(400)).truncatedTo(ChronoUnit.MILLIS);
    Instant plausibleClientUpdatedAt = hoursAgo(2);

    JsonNode body = json(push(token, item(lessonId, farFuture, plausibleClientUpdatedAt)));

    assertThat(body.path("clamped_count").asInt()).isEqualTo(1);
    JsonNode result = body.path("results").get(0);
    assertThat(result.path("clamped").asBoolean()).isTrue();
    // Judged independently: the believable half of the item is untouched.
    assertThat(instant(result.path("server_client_updated_at")))
        .isEqualTo(plausibleClientUpdatedAt);

    JsonNode stored = onlyPulledRow(token);
    assertThat(instant(stored.path("completed_at"))).isEqualTo(instant(body.path("server_time")));
    assertThat(instant(stored.path("client_updated_at"))).isEqualTo(plausibleClientUpdatedAt);
  }

  /**
   * Un-completing a lesson is a real action, and the only way it can reach the server is as an
   * explicit null. A server that read null as "no value supplied" would make the action unsyncable:
   * the completion would come straight back on the next pull.
   */
  @Test
  void anExplicitNullCompletedAtIsStoredAsAnUnCompletion() throws Exception {
    UUID lessonId = newLesson();
    push(token, item(lessonId, hoursAgo(6), hoursAgo(6)));

    JsonNode body = json(push(token, item(lessonId, null, hoursAgo(1))));

    assertThat(body.path("applied_count").asInt()).isEqualTo(1);
    JsonNode stored = onlyPulledRow(token);
    assertThat(stored.path("completed_at").isNull()).isTrue();
    assertThat(instant(stored.path("client_updated_at"))).isEqualTo(hoursAgo(1));
  }

  /**
   * "Required as a key, nullable as a value" is a distinction with a consequence, so the three
   * cases are pinned apart here rather than being left to the reader of the DTO.
   *
   * <p>An item with no {@code completed_at} key is a malformed item, not an un-completion.
   * Accepting it as one would mark a lesson incomplete because a client forgot a field, and the
   * person would lose a completion with nothing anywhere saying so.
   */
  @Test
  void anItemWithNoCompletedAtKeyIsRefusedRatherThanReadAsAnUnCompletion() throws Exception {
    UUID lessonId = newLesson();

    MvcResult result =
        pushRaw(
            token,
            """
            {"items":[{"lesson_id":"%s","client_updated_at":"%s"}]}
            """
                .formatted(lessonId, hoursAgo(2)));

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");
    assertThat(json(pull(token)).path("total_elements").asLong()).isZero();
  }

  /**
   * A malformed item fails the request, not just itself. Per-item {@code REJECTED} is reserved for
   * a well-formed item whose lesson does not resolve -- a fact about the content, which the client
   * can do nothing about and must not be blocked by. A missing key is a fact about the client's own
   * serializer, and answering it item by item would let a broken client keep writing forever while
   * quietly losing part of every batch.
   */
  @Test
  void aSingleMalformedItemFailsTheWholeBatch() throws Exception {
    UUID wellFormed = newLesson();
    UUID malformed = newLesson();

    MvcResult result =
        pushRaw(
            token,
            """
            {"items":[
              {"lesson_id":"%s","completed_at":"%s","client_updated_at":"%s"},
              {"lesson_id":"%s","client_updated_at":"%s"}
            ]}
            """
                .formatted(wellFormed, hoursAgo(2), hoursAgo(2), malformed, hoursAgo(2)));

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");
    // Including the well-formed item that shared the batch with it.
    assertThat(json(pull(token)).path("total_elements").asLong()).isZero();
  }

  @Test
  void aCompletedAtKeyPresentAndNullIsAnUnCompletion() throws Exception {
    UUID lessonId = newLesson();
    Instant clientUpdatedAt = hoursAgo(2);

    MvcResult result =
        pushRaw(
            token,
            """
            {"items":[{"lesson_id":"%s","completed_at":null,"client_updated_at":"%s"}]}
            """
                .formatted(lessonId, clientUpdatedAt));

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(result).path("applied_count").asInt()).isEqualTo(1);
    JsonNode stored = onlyPulledRow(token);
    assertThat(stored.path("completed_at").isNull()).isTrue();
    assertThat(instant(stored.path("client_updated_at"))).isEqualTo(clientUpdatedAt);
  }

  @Test
  void aCompletedAtKeyPresentWithAValueIsACompletion() throws Exception {
    UUID lessonId = newLesson();
    Instant completedAt = hoursAgo(3);

    MvcResult result =
        pushRaw(
            token,
            """
            {"items":[{"lesson_id":"%s","completed_at":"%s","client_updated_at":"%s"}]}
            """
                .formatted(lessonId, completedAt, hoursAgo(2)));

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(result).path("applied_count").asInt()).isEqualTo(1);
    assertThat(instant(onlyPulledRow(token).path("completed_at"))).isEqualTo(completedAt);
  }

  /**
   * A replayed batch is the common case, not the exceptional one: a client that lost the response
   * to its last sync resends the same queue. Nothing about the stored rows may move.
   */
  @Test
  void replayingTheIdenticalBatchLeavesIdenticalState() throws Exception {
    UUID first = newLesson();
    UUID second = newLesson();
    ProgressSyncRequest batch =
        new ProgressSyncRequest(
            List.of(
                new ProgressSyncItem(first, hoursAgo(3), hoursAgo(3)),
                new ProgressSyncItem(second, null, hoursAgo(2))));

    JsonNode firstResponse = json(push(token, batch));
    assertThat(firstResponse.path("applied_count").asInt()).isEqualTo(2);
    List<String> afterFirst = storedRowSnapshot();

    JsonNode secondResponse = json(push(token, batch));

    assertThat(secondResponse.path("applied_count").asInt()).isZero();
    assertThat(secondResponse.path("stale_count").asInt()).isEqualTo(2);
    assertThat(storedRowSnapshot()).isEqualTo(afterFirst);
  }

  // -------------------------------------------------------------------------------------------
  // Batch-level and per-item refusals
  // -------------------------------------------------------------------------------------------

  @Test
  void theSameLessonTwiceInOneBatchIsRefused() throws Exception {
    UUID lessonId = newLesson();
    ProgressSyncRequest batch =
        new ProgressSyncRequest(
            List.of(
                new ProgressSyncItem(lessonId, hoursAgo(3), hoursAgo(3)),
                new ProgressSyncItem(lessonId, hoursAgo(1), hoursAgo(1))));

    MvcResult result = push(token, batch);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("DUPLICATE_ITEM_IN_BATCH");
    // Refused as a whole, so nothing from the batch was stored.
    assertThat(json(pull(token)).path("total_elements").asLong()).isZero();
  }

  @Test
  void aBatchOfFiveHundredAndOneItemsIsRefusedAsTooLarge() throws Exception {
    List<ProgressSyncItem> items = new ArrayList<>(501);
    for (int i = 0; i < 501; i++) {
      items.add(new ProgressSyncItem(UuidV7.randomUuid(), hoursAgo(2), hoursAgo(2)));
    }

    MvcResult result = push(token, new ProgressSyncRequest(items));

    assertThat(result.getResponse().getStatus()).isEqualTo(413);
    assertThat(errorCode(result)).isEqualTo("SYNC_BATCH_TOO_LARGE");
  }

  @Test
  void anEmptyBatchIsAValidationFailure() throws Exception {
    MvcResult result = push(token, new ProgressSyncRequest(List.of()));

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");
  }

  /**
   * The whole point of per-item results: a desktop client can hold weeks of offline writes, and one
   * reference to a lesson an editor has since removed must not block the other several hundred.
   */
  @Test
  void anUnknownLessonIsRejectedPerItemWithoutFailingTheBatch() throws Exception {
    UUID known = newLesson();
    UUID unknown = UuidV7.randomUuid();
    ProgressSyncRequest batch =
        new ProgressSyncRequest(
            List.of(
                new ProgressSyncItem(known, hoursAgo(2), hoursAgo(2)),
                new ProgressSyncItem(unknown, hoursAgo(2), hoursAgo(2))));

    MvcResult response = push(token, batch);
    assertThat(response.getResponse().getStatus()).isEqualTo(200);

    JsonNode body = json(response);
    assertThat(body.path("applied_count").asInt()).isEqualTo(1);
    assertThat(body.path("rejected_count").asInt()).isEqualTo(1);

    JsonNode rejected = body.path("results").get(1);
    assertThat(rejected.path("lesson_id").asString()).isEqualTo(unknown.toString());
    assertThat(rejected.path("status").asString()).isEqualTo("REJECTED");
    assertThat(rejected.path("code").asString()).isEqualTo("LESSON_NOT_FOUND");
    assertThat(rejected.path("server_client_updated_at").isNull()).isTrue();

    assertThat(json(pull(token)).path("total_elements").asLong()).isEqualTo(1);
  }

  @Test
  void aSoftDeletedLessonIsRejectedTheSameWayAsAnUnknownOne() throws Exception {
    UUID lessonId = newLesson();
    softDelete(lessonId);

    JsonNode body = json(push(token, item(lessonId, hoursAgo(2), hoursAgo(2))));

    assertThat(body.path("rejected_count").asInt()).isEqualTo(1);
    assertThat(body.path("results").get(0).path("code").asString()).isEqualTo("LESSON_NOT_FOUND");
  }

  /**
   * A completion is a fact about a person's history; an editorial decision does not unmake it. The
   * row stays readable through the pull direction even though the push direction will no longer
   * accept new state for it.
   */
  @Test
  void aProgressRowSurvivesTheDeletionOfItsLesson() throws Exception {
    UUID lessonId = newLesson();
    Instant completedAt = hoursAgo(3);
    push(token, item(lessonId, completedAt, hoursAgo(3)));

    softDelete(lessonId);

    JsonNode stored = onlyPulledRow(token);
    assertThat(stored.path("lesson_id").asString()).isEqualTo(lessonId.toString());
    assertThat(instant(stored.path("completed_at"))).isEqualTo(completedAt);
  }

  // -------------------------------------------------------------------------------------------
  // Isolation between accounts
  // -------------------------------------------------------------------------------------------

  @Test
  void onePersonsBatchNeverOverwritesAnothersRow() throws Exception {
    UUID lessonId = newLesson();
    Instant mine = hoursAgo(10);
    push(token, item(lessonId, mine, mine));

    String otherToken = newUserToken();
    Instant theirs = hoursAgo(1);
    JsonNode theirResponse = json(push(otherToken, item(lessonId, theirs, theirs)));

    // Strictly newer, and still an insert rather than an overwrite: it is a different row.
    assertThat(theirResponse.path("applied_count").asInt()).isEqualTo(1);
    assertThat(instant(onlyPulledRow(token).path("client_updated_at"))).isEqualTo(mine);
    assertThat(instant(onlyPulledRow(otherToken).path("client_updated_at"))).isEqualTo(theirs);
  }

  @Test
  void thePullDirectionReturnsOnlyTheCallersOwnRows() throws Exception {
    UUID mineOnly = newLesson();
    UUID theirsOnly = newLesson();
    push(token, item(mineOnly, hoursAgo(2), hoursAgo(2)));

    String otherToken = newUserToken();
    push(otherToken, item(theirsOnly, hoursAgo(2), hoursAgo(2)));

    assertThat(onlyPulledRow(token).path("lesson_id").asString()).isEqualTo(mineOnly.toString());
    assertThat(onlyPulledRow(otherToken).path("lesson_id").asString())
        .isEqualTo(theirsOnly.toString());
  }

  // -------------------------------------------------------------------------------------------
  // The pull direction: since, paging and sort
  // -------------------------------------------------------------------------------------------

  /**
   * {@code since} is exclusive, so a client can pass back the newest {@code updated_at} it already
   * holds and get only what it has not seen. An inclusive bound would re-send that row on every
   * poll for as long as the client kept polling.
   */
  @Test
  void sinceIsAnExclusiveLowerBoundOnTheServersOwnUpdatedAt() throws Exception {
    UUID lessonId = newLesson();
    JsonNode pushed = json(push(token, item(lessonId, hoursAgo(2), hoursAgo(2))));
    Instant serverTime = instant(pushed.path("server_time"));

    assertThat(json(pullSince(token, serverTime)).path("total_elements").asLong()).isZero();
    assertThat(json(pullSince(token, serverTime.minusMillis(1))).path("total_elements").asLong())
        .isEqualTo(1);
  }

  /**
   * Paging has to stay stable across rows that share an {@code updated_at}, and one batch stamps
   * every row it writes with a single server instant -- so three rows written together are exactly
   * the case that breaks an order defined by {@code updated_at} alone. Each row must appear on
   * exactly one page.
   */
  @Test
  void rowsWrittenInOneBatchPageWithoutRepeatingOrLosingAny() throws Exception {
    UUID first = newLesson();
    UUID second = newLesson();
    UUID third = newLesson();
    push(
        token,
        new ProgressSyncRequest(
            List.of(
                new ProgressSyncItem(first, hoursAgo(3), hoursAgo(3)),
                new ProgressSyncItem(second, hoursAgo(2), hoursAgo(2)),
                new ProgressSyncItem(third, null, hoursAgo(1)))));

    JsonNode pageZero = json(pullPaged(token, 0, 2));
    JsonNode pageOne = json(pullPaged(token, 1, 2));

    assertThat(pageZero.path("total_elements").asLong()).isEqualTo(3);
    assertThat(pageZero.path("total_pages").asInt()).isEqualTo(2);
    assertThat(pageZero.path("items")).hasSize(2);
    assertThat(pageOne.path("items")).hasSize(1);

    Set<String> seen = new HashSet<>();
    pageZero.path("items").forEach(node -> seen.add(node.path("lesson_id").asString()));
    pageOne.path("items").forEach(node -> seen.add(node.path("lesson_id").asString()));
    assertThat(seen)
        .containsExactlyInAnyOrder(first.toString(), second.toString(), third.toString());
  }

  @Test
  void aSizeAboveTheCeilingIsRefused() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(PROGRESS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .param("size", "101"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("PAGE_SIZE_EXCEEDED");
  }

  /**
   * A single {@code sort=updated_at,desc} occurrence reaches the controller as two list elements,
   * because the framework's own conversion to {@code List<String>} splits on the comma first. A
   * parser that treated every element as an independent field would reject {@code desc} here as an
   * unknown sort field.
   */
  @Test
  void anExplicitFieldAndDirectionPairIsAcceptedAsOneSort() throws Exception {
    push(token, item(newLesson(), hoursAgo(2), hoursAgo(2)));

    MvcResult result =
        mockMvc
            .perform(
                get(PROGRESS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .param("sort", "updated_at,desc"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(result).path("items")).hasSize(1);
  }

  @Test
  void aSinceValueWithNoZoneOffsetIsRefusedRatherThanGuessedAt() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(PROGRESS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .param("since", "2026-09-01T10:00:00"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("INVALID_PARAMETER");
  }

  // -------------------------------------------------------------------------------------------
  // Throttling
  // -------------------------------------------------------------------------------------------

  /**
   * The budget is lowered for the duration of this test rather than configured low for the whole
   * suite, for the same reason the anonymous read limiter's test does it: a production-sized budget
   * in the test profile would make a green run depend on how many calls the classes ahead of this
   * one happened to make in the same window.
   */
  @Test
  void aCallerOverTheSyncBudgetIsThrottledWithRetryAfterAndTheTwoDirectionsAreSeparate()
      throws Exception {
    UUID lessonId = newLesson();
    rateLimits.setSyncProgressPerHour(1);

    assertThat(push(token, item(lessonId, hoursAgo(3), hoursAgo(3))).getResponse().getStatus())
        .isEqualTo(200);

    MvcResult throttled = push(token, item(lessonId, hoursAgo(2), hoursAgo(2)));
    assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
    assertThat(errorCode(throttled)).isEqualTo("RATE_LIMITED");
    assertThat(Integer.parseInt(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
        .isBetween(1, 3600);

    // The pull direction holds its own budget: a device that reads too often must not thereby lose
    // its ability to upload.
    assertThat(pull(token).getResponse().getStatus()).isEqualTo(200);
  }

  // -------------------------------------------------------------------------------------------
  // Fixtures and helpers
  // -------------------------------------------------------------------------------------------

  private String newUserToken() throws Exception {
    String issued = userToken();
    touchedUserIds.add(jwt.verify(issued).userId());
    return issued;
  }

  private UUID newLesson() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Lesson lesson = new Lesson();
    lesson.setId(UuidV7.randomUuid());
    lesson.setModuleId(moduleId);
    lesson.setSlug(uniqueSlug("progress-sync-lesson"));
    lesson.setTitle("Progress sync fixture lesson");
    lesson.setBodyMarkdown("Fixture body.");
    lesson.setDifficulty(Difficulty.BEGINNER);
    lesson.setDisplayOrder(++lessonCount);
    lesson.setContentVersion(1);
    lesson.setCreatedAt(now);
    lesson.setUpdatedAt(now);
    return lessons.saveAndFlush(lesson).getId();
  }

  private void softDelete(UUID lessonId) {
    Lesson lesson = lessons.findById(lessonId).orElseThrow();
    lesson.setDeletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    lessons.saveAndFlush(lesson);
  }

  private static ProgressSyncRequest item(
      UUID lessonId, Instant completedAt, Instant clientUpdatedAt) {
    return new ProgressSyncRequest(
        List.of(new ProgressSyncItem(lessonId, completedAt, clientUpdatedAt)));
  }

  private MvcResult push(String bearerToken, ProgressSyncRequest batch) throws Exception {
    return mockMvc
        .perform(
            post(PROGRESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(batch)))
        .andReturn();
  }

  /**
   * Posts a body written out by hand. The presence or absence of a JSON key is exactly what these
   * cases are about, and a body serialized from the DTO cannot express "this key is not here" --
   * the record component would emit it as null, which is the other case entirely.
   */
  private MvcResult pushRaw(String bearerToken, String body) throws Exception {
    return mockMvc
        .perform(
            post(PROGRESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private MvcResult pull(String bearerToken) throws Exception {
    return mockMvc
        .perform(get(PROGRESS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(bearerToken)))
        .andReturn();
  }

  private MvcResult pullSince(String bearerToken, Instant since) throws Exception {
    return mockMvc
        .perform(
            get(PROGRESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(bearerToken))
                .param("since", since.toString()))
        .andReturn();
  }

  private MvcResult pullPaged(String bearerToken, int page, int size) throws Exception {
    return mockMvc
        .perform(
            get(PROGRESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(bearerToken))
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(size)))
        .andReturn();
  }

  /** The caller's single stored row, asserting on the way that there is exactly one. */
  private JsonNode onlyPulledRow(String bearerToken) throws Exception {
    JsonNode body = json(pull(bearerToken));
    assertThat(body.path("total_elements").asLong()).isEqualTo(1);
    return body.path("items").get(0);
  }

  /** Every stored row of the account under test, as comparable text, for a before/after check. */
  private List<String> storedRowSnapshot() {
    return new JdbcTemplate(dataSource)
            .queryForList(
                "SELECT lesson_id, completed_at, client_updated_at, updated_at FROM user_progress"
                    + " WHERE user_id = ? ORDER BY lesson_id",
                userId)
            .stream()
            .map(Object::toString)
            .toList();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  /**
   * A timestamp relative to one instant captured when the test started, not to {@code now()} read
   * afresh. Several tests submit a value and then assert that the same value came back, and a
   * helper whose answer moved between the two calls would compare a timestamp against a slightly
   * different one and fail for a reason that has nothing to do with the rule under test.
   */
  private Instant hoursAgo(int hours) {
    return testStart.minus(Duration.ofHours(hours));
  }

  private static Instant instant(JsonNode node) {
    return Instant.parse(node.asString());
  }
}
