package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.bytelore.server.content.admin.dto.CreateCodeExampleRequest;
import dev.bytelore.server.content.admin.dto.CreateModuleRequest;
import dev.bytelore.server.content.admin.dto.CreateTrackRequest;
import dev.bytelore.server.content.admin.dto.MindMapNodeRequest;
import dev.bytelore.server.content.admin.dto.MindMapUpsertRequest;
import dev.bytelore.server.translation.dto.TranslationUpsertRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Builds one realistic published track through the admin API and tears it down again, for the two
 * suites that exercise the content sync protocol's read side.
 *
 * <p>The fixture is created through the API rather than by inserting rows, because the packaged
 * bytes and the digest over them are produced by the write path. Rows inserted directly would carry
 * null package columns, and a manifest that advertised nothing would let every assertion below pass
 * vacuously.
 *
 * <p>Everything is torn down child-first in an {@code @AfterEach}. The Testcontainer is shared
 * across test classes through Spring's context cache, so a track left behind is visible to every
 * other class in the run -- and the foreign keys between tracks, modules, lessons, code examples
 * and translations are deliberately not {@code ON DELETE CASCADE}.
 */
abstract class ManifestFixtureSupport extends ContentApiTestSupport {

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void removeManifestFixtures() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  /**
   * A published track with two modules, three lessons, a code example, translations in two locales
   * and a mind map.
   *
   * <p>The translations are written {@code tr} first and {@code fr} second on purpose: §5 sorts
   * translations by locale, so a manifest that emitted them in insertion order would put {@code tr}
   * ahead of {@code fr} and the ordering assertions would catch it.
   */
  protected TrackFixture createPublishedTrack(String token) throws Exception {
    CreateTrackRequest trackBody =
        new CreateTrackRequest(
            uniqueSlug("manifest-track"),
            "Manifest Fixture Track",
            "A track that exists to be manifested.",
            "angular",
            null,
            true);
    UUID trackId =
        UUID.fromString(
            json(created(post("/api/v1/admin/tracks"), token, trackBody)).path("id").asString());
    createdTrackIds.add(trackId);

    UUID moduleA = createModule(token, trackId, "Signals and Reactivity", 1, 60);
    UUID moduleB = createModule(token, trackId, "Routing and Navigation", 2, 45);

    UUID lessonOne =
        createLesson(token, moduleA, uniqueSlug("signals-basics"), "Introduction to signals");
    UUID lessonTwo = createLesson(token, moduleA, uniqueSlug("computed-values"), "Computed values");
    UUID lessonThree =
        createLesson(token, moduleB, uniqueSlug("router-outlet"), "The router outlet");

    createCodeExample(token, lessonOne, "typescript", "const count = signal(0);\ncount.set(1);");

    upsertTranslation(
        token, "TRACK", trackId, "tr", "Manifest Deneme Yolu", "Manifest icin var olan bir yol.");
    upsertTranslation(
        token,
        "TRACK",
        trackId,
        "fr",
        "Piste de manifeste",
        "Une piste qui existe pour le manifeste.");
    upsertTranslation(token, "MODULE", moduleA, "tr", "Sinyaller ve Reaktivite", null);
    upsertTranslation(
        token,
        "LESSON",
        lessonOne,
        "tr",
        "Sinyallere giris",
        "# Sinyaller\n\nBir sinyal bir degerdir.");
    upsertTranslation(
        token,
        "LESSON",
        lessonOne,
        "fr",
        "Introduction aux signaux",
        "# Signaux\n\nUn signal est une valeur.");

    UUID mindMapId = upsertMindMap(token, trackId, lessonOne);

    return new TrackFixture(
        trackId, moduleA, moduleB, List.of(lessonOne, lessonTwo, lessonThree), mindMapId);
  }

  /** An unpublished track with one lesson, for the "not published" halves of the error tables. */
  protected TrackFixture createUnpublishedTrack(String token) throws Exception {
    CreateTrackRequest trackBody =
        new CreateTrackRequest(
            uniqueSlug("draft-track"), "Draft Fixture Track", null, null, null, false);
    UUID trackId =
        UUID.fromString(
            json(created(post("/api/v1/admin/tracks"), token, trackBody)).path("id").asString());
    createdTrackIds.add(trackId);
    UUID moduleId = createModule(token, trackId, "Draft Module", 1, null);
    UUID lessonId = createLesson(token, moduleId, uniqueSlug("draft-lesson"), "Draft lesson");
    return new TrackFixture(trackId, moduleId, null, List.of(lessonId), null);
  }

  protected UUID createModule(
      String token, UUID trackId, String title, Integer order, Integer estimatedMinutes)
      throws Exception {
    CreateModuleRequest body = new CreateModuleRequest(title, order, estimatedMinutes);
    return UUID.fromString(
        json(created(post("/api/v1/admin/tracks/" + trackId + "/modules"), token, body))
            .path("id")
            .asString());
  }

  protected UUID createLesson(String token, UUID moduleId, String slug, String title)
      throws Exception {
    String payload =
        ("{\"slug\":\"%s\",\"title\":\"%s\","
                + "\"body_markdown\":\"# %s\\n\\nA body with two trailing spaces  \\nand a second line.\","
                + "\"difficulty\":\"INTERMEDIATE\",\"estimated_minutes\":25}")
            .formatted(slug, title, title);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(json(result).path("id").asString());
  }

  protected void createCodeExample(String token, UUID lessonId, String language, String code)
      throws Exception {
    CreateCodeExampleRequest body =
        new CreateCodeExampleRequest(language, code, "A writable signal", null);
    created(post("/api/v1/admin/lessons/" + lessonId + "/code-examples"), token, body);
  }

  protected void upsertTranslation(
      String token, String entityType, UUID entityId, String locale, String title, String body)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/%s/%s/%s".formatted(entityType, entityId, locale))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new TranslationUpsertRequest(title, body, null))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 201);
  }

  protected UUID upsertMindMap(String token, UUID trackId, UUID lessonId) throws Exception {
    MindMapNodeRequest child = new MindMapNodeRequest("signals", "Signals", lessonId, List.of());
    MindMapUpsertRequest body =
        new MindMapUpsertRequest(
            new MindMapNodeRequest("root", "Manifest Fixture Track", null, List.of(child)), null);
    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 201);
    return UUID.fromString(json(result).path("id").asString());
  }

  private MvcResult created(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
      String token,
      Object body)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                builder
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .withFailMessage("Fixture request failed: %s", result.getResponse().getContentAsString())
        .isEqualTo(201);
    return result;
  }

  /**
   * SHA-256 over the bytes a response actually delivered, computed here rather than through the
   * production helper. A verification that reused the code under test could only prove the server
   * agrees with itself.
   */
  protected static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @param trackId the published track
   * @param moduleAId first module, holding two lessons
   * @param moduleBId second module, holding one lesson; null on the unpublished fixture
   * @param lessonIds lesson identifiers in creation order
   * @param mindMapId the mind map's own identifier, distinct from the track's
   */
  protected record TrackFixture(
      UUID trackId, UUID moduleAId, UUID moduleBId, List<UUID> lessonIds, UUID mindMapId) {}
}
