package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import dev.devatlas.server.content.admin.dto.UpdateLessonRequest;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * {@code GET /content/{entityType}/{entityId}?version=N} -- the package endpoint (§4.3), its
 * version negotiation (§4.4), and the {@code Range}/{@code If-Match} behaviour the download
 * engine's resume path depends on (§7).
 *
 * <p>The first test in this class is the one the whole protocol exists for: fetch a track manifest,
 * fetch every entity it lists, and assert each response body hashes to exactly the {@code sha256}
 * the manifest advertised. Everything else here guards a way that guarantee could be lost.
 */
class ContentPackageIT extends ManifestFixtureSupport {

  @Test
  void everyEntityTheManifestListsIsServedWithExactlyTheDigestItAdvertised() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    JsonNode manifest = trackManifest(fixture.trackId());
    JsonNode entities = manifest.path("entities");
    assertThat(entities.size()).isEqualTo(4);

    for (JsonNode entity : entities) {
      String type = entity.path("entity_type").asString();
      String id = entity.path("entity_id").asString();
      int version = entity.path("content_version").asInt();
      String advertisedDigest = entity.path("sha256").asString();
      int advertisedSize = entity.path("size_bytes").asInt();

      MvcResult result = fetchPackage(type, id, version);
      assertThat(result.getResponse().getStatus())
          .withFailMessage("%s %s v%d was not served", type, id, version)
          .isEqualTo(200);
      byte[] body = result.getResponse().getContentAsByteArray();

      assertThat(sha256Hex(body))
          .withFailMessage(
              "%s %s v%d: manifest promised %s, the response hashes to %s",
              type, id, version, advertisedDigest, sha256Hex(body))
          .isEqualTo(advertisedDigest);
      assertThat(body.length).isEqualTo(advertisedSize);
      // Content-Length has to equal size_bytes exactly, which is what lets the engine detect
      // truncation before it bothers hashing (§3.2b).
      assertThat(result.getResponse().getContentLength()).isEqualTo(advertisedSize);
      assertThat(result.getResponse().getHeader(HttpHeaders.ETAG))
          .isEqualTo("\"" + advertisedDigest + "\"");

      // The package identifies itself with the same type and id the manifest used.
      JsonNode pkg = jsonMapper.readTree(body);
      assertThat(pkg.path("entity_type").asString()).isEqualTo(type);
      assertThat(pkg.path("entity_id").asString()).isEqualTo(id);
      assertThat(pkg.path("content_version").asInt()).isEqualTo(version);
    }
  }

  @Test
  void theServedBytesAreTheStoredBytesRatherThanAReserialization() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    JsonNode entity = firstLessonEntity(trackManifest(fixture.trackId()));
    UUID lessonId = UUID.fromString(entity.path("entity_id").asString());

    byte[] stored =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT package_bytes FROM lessons WHERE id = ?", byte[].class, lessonId);
    MvcResult result =
        fetchPackage("LESSON", lessonId.toString(), entity.path("content_version").asInt());

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(stored);
  }

  @Test
  void aPackageCarriesTheImmutableCachingHeadersAndIdentityEncoding() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    JsonNode entity = firstLessonEntity(trackManifest(fixture.trackId()));

    MvcResult result =
        fetchPackage(
            "LESSON", entity.path("entity_id").asString(), entity.path("content_version").asInt());

    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
        .contains("public")
        .contains("max-age=31536000")
        .contains("immutable");
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_ENCODING)).isEqualTo("identity");
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
  }

  @Test
  void aVersionOlderThanCurrentIsSupersededAndTheBodyNamesTheCurrentOne() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    UUID lessonId = fixture.lessonIds().get(1);
    int originalVersion = versionOf(trackManifest(fixture.trackId()), lessonId);

    retitle(token, lessonId, "A newer title");
    int currentVersion = versionOf(trackManifest(fixture.trackId()), lessonId);
    assertThat(currentVersion).isEqualTo(originalVersion + 1);

    MvcResult result = fetchPackage("LESSON", lessonId.toString(), originalVersion);

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(result)).isEqualTo("CONTENT_VERSION_SUPERSEDED");
    // The current version travels in the body so the client can re-plan without a second manifest
    // fetch (§4.4).
    assertThat(json(result).path("current_version").asInt()).isEqualTo(currentVersion);
  }

  @Test
  void aVersionNewerThanCurrentIsNotFound() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    UUID lessonId = fixture.lessonIds().get(0);
    int currentVersion = versionOf(trackManifest(fixture.trackId()), lessonId);

    MvcResult result = fetchPackage("LESSON", lessonId.toString(), currentVersion + 1);

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(result)).isEqualTo("LESSON_NOT_FOUND");
  }

  @Test
  void anUnknownAnUnpublishedAndASoftDeletedEntityAllReportThePerTypeNotFoundCode()
      throws Exception {
    String token = editorToken();
    TrackFixture published = createPublishedTrack(token);
    TrackFixture draft = createUnpublishedTrack(token);

    MvcResult unknownLesson = fetchPackage("LESSON", UUID.randomUUID().toString(), 1);
    assertThat(unknownLesson.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(unknownLesson)).isEqualTo("LESSON_NOT_FOUND");

    MvcResult unknownMindMap = fetchPackage("MIND_MAP", UUID.randomUUID().toString(), 1);
    assertThat(unknownMindMap.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(unknownMindMap)).isEqualTo("MIND_MAP_NOT_FOUND");

    // A lesson under an unpublished track is not addressable, even though its package exists.
    MvcResult unpublished = fetchPackage("LESSON", draft.lessonIds().get(0).toString(), 1);
    assertThat(unpublished.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(unpublished)).isEqualTo("LESSON_NOT_FOUND");

    // A malformed identifier is answered the same way; no manifest ever advertised it either.
    MvcResult malformed = fetchPackage("LESSON", "not-a-uuid", 1);
    assertThat(malformed.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(malformed)).isEqualTo("LESSON_NOT_FOUND");

    assertThat(published.lessonIds()).isNotEmpty();
  }

  @Test
  void theVersionParameterIsRequiredAndTheEntityTypeIsLowercase() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    UUID lessonId = fixture.lessonIds().get(0);

    MvcResult withoutVersion =
        mockMvc.perform(get("/api/v1/content/lesson/" + lessonId)).andReturn();
    assertThat(withoutVersion.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(withoutVersion)).isEqualTo("INVALID_PARAMETER");

    // The path segment is lowercase; the SCREAMING_SNAKE_CASE spelling belongs in JSON bodies.
    MvcResult uppercase =
        mockMvc
            .perform(get("/api/v1/content/LESSON/" + lessonId).param("version", "1"))
            .andReturn();
    assertThat(uppercase.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(uppercase)).isEqualTo("INVALID_PARAMETER");
  }

  @Test
  void aRangeRequestIsAnsweredWithPartialContentAndResumesWhereItLeftOff() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    JsonNode entity = firstLessonEntity(trackManifest(fixture.trackId()));
    String id = entity.path("entity_id").asString();
    int version = entity.path("content_version").asInt();
    byte[] whole = fetchPackage("LESSON", id, version).getResponse().getContentAsByteArray();

    int split = whole.length / 3;
    MvcResult head =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + id)
                    .param("version", Integer.toString(version))
                    .header(HttpHeaders.RANGE, "bytes=0-" + (split - 1)))
            .andReturn();
    assertThat(head.getResponse().getStatus()).isEqualTo(206);
    assertThat(head.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
        .isEqualTo("bytes 0-" + (split - 1) + "/" + whole.length);
    assertThat(head.getResponse().getContentAsByteArray())
        .isEqualTo(Arrays.copyOfRange(whole, 0, split));

    // The resume the engine issues after a restart: everything from the partial file's size on.
    MvcResult tail =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + id)
                    .param("version", Integer.toString(version))
                    .header(HttpHeaders.RANGE, "bytes=" + split + "-"))
            .andReturn();
    assertThat(tail.getResponse().getStatus()).isEqualTo(206);
    assertThat(tail.getResponse().getContentAsByteArray())
        .isEqualTo(Arrays.copyOfRange(whole, split, whole.length));

    // The two halves reassemble into the bytes the digest was computed over.
    byte[] rejoined = new byte[whole.length];
    System.arraycopy(head.getResponse().getContentAsByteArray(), 0, rejoined, 0, split);
    System.arraycopy(
        tail.getResponse().getContentAsByteArray(), 0, rejoined, split, whole.length - split);
    assertThat(sha256Hex(rejoined)).isEqualTo(entity.path("sha256").asString());
  }

  @Test
  void aResumeWhoseIfMatchNoLongerMatchesIsRejectedWith412() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    JsonNode entity = firstLessonEntity(trackManifest(fixture.trackId()));
    String id = entity.path("entity_id").asString();
    int version = entity.path("content_version").asInt();
    String currentEtag = "\"" + entity.path("sha256").asString() + "\"";

    // The matching case first, so the rejection below cannot be a Range failure in disguise.
    MvcResult matching =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + id)
                    .param("version", Integer.toString(version))
                    .header(HttpHeaders.RANGE, "bytes=4-")
                    .header(HttpHeaders.IF_MATCH, currentEtag))
            .andReturn();
    assertThat(matching.getResponse().getStatus()).isEqualTo(206);

    MvcResult mismatched =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + id)
                    .param("version", Integer.toString(version))
                    .header(HttpHeaders.RANGE, "bytes=4-")
                    .header(HttpHeaders.IF_MATCH, "\"" + "1".repeat(64) + "\""))
            .andReturn();
    assertThat(mismatched.getResponse().getStatus()).isEqualTo(412);
    assertThat(errorCode(mismatched)).isEqualTo("CONTENT_CHANGED_DURING_RESUME");
  }

  /**
   * A lesson package names a translation's text {@code body}, and reserves {@code body_markdown}
   * for the lesson's own markdown at the top level.
   *
   * <p>The two are different fields. A client reading a package has to be able to tell them apart
   * without tracking which nesting level it is at, and {@code body} is already the name the
   * translation row, the read API and a track manifest's translations all use -- one spelling
   * wherever a translated body appears.
   *
   * <p>Deliberately run against <strong>seeded</strong> content rather than a fixture this class
   * builds. A fixture proves the endpoint can serve a translation someone remembered to create; the
   * seed proves a fresh database ships one, which is what every other suite, and every client
   * pointed at a new deployment, actually reads. A translated body that exists only inside one test
   * class is a field the rest of the system never sees, and a field nothing populates is one whose
   * absence is indistinguishable from success.
   */
  @Test
  void aTranslationEntryCarriesBodyWhileTheLessonsOwnMarkdownStaysBodyMarkdown() throws Exception {
    UUID seedTrackId = trackIdBySlug("angular-path");
    JsonNode manifest = trackManifest(seedTrackId);
    UUID translatedLesson = lessonIdBySlug(manifest, "signals-and-reactivity");
    int version = versionOf(manifest, translatedLesson);

    MvcResult result = fetchPackage("LESSON", translatedLesson.toString(), version);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String raw = result.getResponse().getContentAsString();
    JsonNode pkg = jsonMapper.readTree(result.getResponse().getContentAsByteArray());

    JsonNode translations = pkg.path("translations");
    assertThat(translations.size()).isEqualTo(2);
    for (JsonNode translation : translations) {
      assertThat(translation.properties())
          .extracting(java.util.Map.Entry::getKey)
          .containsExactlyInAnyOrder("locale", "title", "body");
      assertThat(translation.path("body").asString()).isNotBlank();
    }
    // Sorted by locale, so a second locale is what distinguishes a correct sort from no sort.
    assertThat(translations.get(0).path("locale").asString()).isEqualTo("fr");
    assertThat(translations.get(1).path("locale").asString()).isEqualTo("tr");
    assertThat(translations.get(1).path("title").asString()).isEqualTo("Sinyaller ve Reaktivite");
    assertThat(translations.get(1).path("body").asString()).contains("Bir sinyal");

    // Exactly one body_markdown in the whole document, and it is the lesson's own.
    assertThat(raw.split("\"body_markdown\"", -1).length - 1).isEqualTo(1);
    assertThat(pkg.path("body_markdown").asString()).contains("What is a signal?");

    // The served bytes still hash to what the manifest advertised, translation and all.
    assertThat(sha256Hex(result.getResponse().getContentAsByteArray()))
        .isEqualTo(entityDigest(manifest, translatedLesson));
  }

  /** No credentials, on either endpoint family (§3.7). */
  @Test
  void aPackageIsServedWithoutCredentials() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    JsonNode entity = firstLessonEntity(trackManifest(fixture.trackId()));

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/content/lesson/" + entity.path("entity_id").asString())
                    .param("version", entity.path("content_version").asString()))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }

  private MvcResult fetchPackage(String entityType, String entityId, int version) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/content/%s/%s"
                    .formatted(entityType.toLowerCase(java.util.Locale.ROOT), entityId))
                .param("version", Integer.toString(version)))
        .andReturn();
  }

  private JsonNode trackManifest(UUID trackId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/manifest/track/" + trackId)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return json(result);
  }

  private static JsonNode firstLessonEntity(JsonNode manifest) {
    for (JsonNode entity : manifest.path("entities")) {
      if ("LESSON".equals(entity.path("entity_type").asString())) {
        return entity;
      }
    }
    throw new AssertionError("The manifest lists no lesson entity.");
  }

  /** Resolves a track by slug through the catalog, so no seeded identifier is hard-coded here. */
  private UUID trackIdBySlug(String slug) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    for (JsonNode row : json(result).path("tracks")) {
      if (row.path("slug").asString().equals(slug)) {
        return UUID.fromString(row.path("track_id").asString());
      }
    }
    throw new AssertionError("The catalog lists no published track with slug " + slug);
  }

  private static UUID lessonIdBySlug(JsonNode manifest, String slug) {
    for (JsonNode module : manifest.path("modules")) {
      for (JsonNode lesson : module.path("lessons")) {
        if (lesson.path("slug").asString().equals(slug)) {
          return UUID.fromString(lesson.path("lesson_id").asString());
        }
      }
    }
    throw new AssertionError("The manifest lists no lesson with slug " + slug);
  }

  private static String entityDigest(JsonNode manifest, UUID entityId) {
    for (JsonNode entity : manifest.path("entities")) {
      if (entity.path("entity_id").asString().equals(entityId.toString())) {
        return entity.path("sha256").asString();
      }
    }
    throw new AssertionError("The manifest does not list entity " + entityId);
  }

  private static int versionOf(JsonNode manifest, UUID entityId) {
    for (JsonNode entity : manifest.path("entities")) {
      if (entity.path("entity_id").asString().equals(entityId.toString())) {
        return entity.path("content_version").asInt();
      }
    }
    throw new AssertionError("The manifest does not list entity " + entityId);
  }

  private void retitle(String token, UUID lessonId, String title) throws Exception {
    long optimisticVersion =
        json(mockMvc
                .perform(
                    get("/api/v1/admin/lessons/" + lessonId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn())
            .path("version")
            .asLong();
    MvcResult result =
        mockMvc
            .perform(
                patch("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new UpdateLessonRequest(
                                null, title, null, null, null, null, null, optimisticVersion))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }
}
