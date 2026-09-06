package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import dev.bytelore.server.content.admin.dto.UpdateLessonRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The two manifest endpoints of the content sync protocol: §4.1 catalog, §4.2 track manifest, §5
 * ordering, and the byte stability both of them depend on.
 *
 * <p>Every assertion is scoped to this class's own fixture track. The Testcontainer is shared
 * across test classes through Spring's context cache, and the seed migration publishes a track of
 * its own, so anything written as "the catalog contains exactly one row" would pass or fail
 * depending on which classes ran first.
 */
class ManifestIT extends ManifestFixtureSupport {

  @Test
  void theCatalogListsEveryPublishedTrackWithItsCountsAndSizes() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    MvcResult result = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).matches("\"[0-9a-f]{64}\"");
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);

    String body = result.getResponse().getContentAsString();
    // §4.1 as implemented: no generated_at. A timestamp in the body would make every generation
    // differ and there would be nothing for an ETag to be stable about.
    assertThat(body).doesNotContain("generated_at");

    JsonNode row = catalogRow(json(result), fixture.trackId());
    assertThat(row).isNotNull();
    assertThat(row.path("slug").asString()).isNotEmpty();
    assertThat(row.path("title").asString()).isEqualTo("Manifest Fixture Track");
    assertThat(row.path("lesson_count").asInt()).isEqualTo(3);
    assertThat(row.path("content_version").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(row.path("updated_at").asString())
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

    // total_size_bytes is the sum of the entity package sizes, so a client can show a download
    // size before committing the user to it.
    long entitiesTotal = 0;
    for (JsonNode entity : trackManifest(fixture.trackId()).path("entities")) {
      entitiesTotal += entity.path("size_bytes").asLong();
    }
    assertThat(row.path("total_size_bytes").asLong()).isEqualTo(entitiesTotal).isPositive();
  }

  @Test
  void anUnpublishedTrackIsAbsentFromTheCatalog() throws Exception {
    String token = editorToken();
    TrackFixture draft = createUnpublishedTrack(token);

    MvcResult result = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(catalogRow(json(result), draft.trackId())).isNull();
  }

  @Test
  void aMatchingIfNoneMatchIsAnsweredWith304AndNoBody() throws Exception {
    String token = editorToken();
    createPublishedTrack(token);

    MvcResult first = mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn();
    String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

    MvcResult revalidated =
        mockMvc
            .perform(get("/api/v1/manifest/catalog").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andReturn();

    assertThat(revalidated.getResponse().getStatus()).isEqualTo(304);
    assertThat(revalidated.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(revalidated.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(etag);

    // A stale tag revalidates into a full response rather than a 304.
    MvcResult stale =
        mockMvc
            .perform(
                get("/api/v1/manifest/catalog")
                    .header(HttpHeaders.IF_NONE_MATCH, "\"" + "0".repeat(64) + "\""))
            .andReturn();
    assertThat(stale.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void aTrackManifestCarriesTheStructuralSummaryAndTheEntityList() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    MvcResult result =
        mockMvc.perform(get("/api/v1/manifest/track/" + fixture.trackId())).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("generated_at");

    JsonNode manifest = json(result);
    assertThat(manifest.path("track_id").asString()).isEqualTo(fixture.trackId().toString());
    assertThat(manifest.path("title").asString()).isEqualTo("Manifest Fixture Track");
    assertThat(manifest.path("description").asString())
        .isEqualTo("A track that exists to be manifested.");
    assertThat(manifest.path("icon").asString()).isEqualTo("angular");
    assertThat(manifest.path("content_version").asInt()).isGreaterThanOrEqualTo(1);

    // Track translations carry the translated description as `body`; no package ships a track's
    // own text, so this is the only place a desktop client can read it.
    JsonNode trackTranslations = manifest.path("translations");
    assertThat(localesOf(trackTranslations)).containsExactly("fr", "tr");
    assertThat(trackTranslations.get(1).path("body").asString())
        .isEqualTo("Manifest icin var olan bir yol.");

    JsonNode modules = manifest.path("modules");
    assertThat(modules.size()).isEqualTo(2);
    assertThat(modules.get(0).path("module_id").asString())
        .isEqualTo(fixture.moduleAId().toString());
    assertThat(modules.get(0).path("order").asInt()).isEqualTo(1);
    assertThat(modules.get(0).path("estimated_minutes").asInt()).isEqualTo(60);
    assertThat(localesOf(modules.get(0).path("translations"))).containsExactly("tr");
    // A module translation carries the title only: a module has no body of its own.
    assertThat(modules.get(0).path("translations").get(0).properties())
        .extracting(java.util.Map.Entry::getKey)
        .containsExactlyInAnyOrder("locale", "title");
    assertThat(modules.get(1).path("order").asInt()).isEqualTo(2);

    JsonNode lessons = modules.get(0).path("lessons");
    assertThat(lessons.size()).isEqualTo(2);
    assertThat(lessons.get(0).path("lesson_id").asString())
        .isEqualTo(fixture.lessonIds().get(0).toString());
    assertThat(lessons.get(0).path("order").asInt()).isEqualTo(1);
    assertThat(lessons.get(1).path("order").asInt()).isEqualTo(2);
    assertThat(lessons.get(0).path("difficulty").asString()).isEqualTo("INTERMEDIATE");
    assertThat(lessons.get(0).path("estimated_minutes").asInt()).isEqualTo(25);
    assertThat(lessons.get(0).path("slug").asString()).isNotEmpty();
    assertThat(localesOf(lessons.get(0).path("translations"))).containsExactly("fr", "tr");
    assertThat(modules.get(1).path("lessons").size()).isEqualTo(1);
  }

  @Test
  void entitiesCarryExactlyFiveFieldsSortedByTypeThenId() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    JsonNode entities = trackManifest(fixture.trackId()).path("entities");

    // Three lessons plus the mind map.
    assertThat(entities.size()).isEqualTo(4);
    List<String> types = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    for (JsonNode entity : entities) {
      assertThat(entity.properties())
          .extracting(java.util.Map.Entry::getKey)
          .containsExactlyInAnyOrder(
              "entity_type", "entity_id", "content_version", "sha256", "size_bytes");
      assertThat(entity.path("sha256").asString()).matches("[0-9a-f]{64}");
      assertThat(entity.path("size_bytes").asInt()).isPositive();
      assertThat(entity.path("content_version").asInt()).isGreaterThanOrEqualTo(1);
      types.add(entity.path("entity_type").asString());
      ids.add(entity.path("entity_type").asString() + " " + entity.path("entity_id").asString());
    }

    // §5: entity_type first, then entity_id. LESSON sorts before MIND_MAP.
    assertThat(types).containsExactly("LESSON", "LESSON", "LESSON", "MIND_MAP");
    assertThat(ids).isSorted();
    assertThat(entities.get(3).path("entity_id").asString())
        .isEqualTo(fixture.mindMapId().toString());
  }

  @Test
  void twoGenerationsOfUnchangedContentProduceIdenticalBytes() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    MvcResult first =
        mockMvc.perform(get("/api/v1/manifest/track/" + fixture.trackId())).andReturn();
    MvcResult second =
        mockMvc.perform(get("/api/v1/manifest/track/" + fixture.trackId())).andReturn();

    assertThat(second.getResponse().getContentAsByteArray())
        .isEqualTo(first.getResponse().getContentAsByteArray());
    assertThat(second.getResponse().getHeader(HttpHeaders.ETAG))
        .isEqualTo(first.getResponse().getHeader(HttpHeaders.ETAG));
    // The tag is the digest of the bytes that were sent, not of something else that happened to be
    // stable at the same time.
    assertThat(second.getResponse().getHeader(HttpHeaders.ETAG))
        .isEqualTo("\"" + sha256Hex(second.getResponse().getContentAsByteArray()) + "\"");
    assertThat(new String(first.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
        .doesNotEndWith("\n");
  }

  /**
   * The stability test above would pass against a manifest that never changed at all, so this is
   * its other half: an edit to a lesson has to move the manifest's bytes and its tag.
   */
  @Test
  void anEditedLessonChangesTheManifestBytesItsEtagAndTheEntitysDigest() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);
    UUID lessonId = fixture.lessonIds().get(0);

    MvcResult before =
        mockMvc.perform(get("/api/v1/manifest/track/" + fixture.trackId())).andReturn();
    JsonNode entityBefore = entityById(json(before), lessonId);

    long optimisticVersion =
        json(mockMvc
                .perform(
                    get("/api/v1/admin/lessons/" + lessonId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn())
            .path("version")
            .asLong();
    MvcResult updated =
        mockMvc
            .perform(
                patch("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new UpdateLessonRequest(
                                null,
                                "A retitled lesson",
                                null,
                                null,
                                null,
                                null,
                                null,
                                optimisticVersion))))
            .andReturn();
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);

    MvcResult after =
        mockMvc.perform(get("/api/v1/manifest/track/" + fixture.trackId())).andReturn();
    JsonNode entityAfter = entityById(json(after), lessonId);

    assertThat(after.getResponse().getContentAsByteArray())
        .isNotEqualTo(before.getResponse().getContentAsByteArray());
    assertThat(after.getResponse().getHeader(HttpHeaders.ETAG))
        .isNotEqualTo(before.getResponse().getHeader(HttpHeaders.ETAG));
    assertThat(entityAfter.path("content_version").asInt())
        .isEqualTo(entityBefore.path("content_version").asInt() + 1);
    assertThat(entityAfter.path("sha256").asString())
        .isNotEqualTo(entityBefore.path("sha256").asString());
  }

  @Test
  void anUnpublishedOrUnknownTrackIsNotFound() throws Exception {
    String token = editorToken();
    TrackFixture draft = createUnpublishedTrack(token);

    MvcResult unpublished =
        mockMvc.perform(get("/api/v1/manifest/track/" + draft.trackId())).andReturn();
    assertThat(unpublished.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(unpublished)).isEqualTo("TRACK_NOT_FOUND");

    MvcResult unknown =
        mockMvc.perform(get("/api/v1/manifest/track/" + UUID.randomUUID())).andReturn();
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(unknown)).isEqualTo("TRACK_NOT_FOUND");
  }

  /** Neither manifest endpoint accepts or requires credentials (§3.7). */
  @Test
  void bothManifestEndpointsAnswerWithoutCredentials() throws Exception {
    String token = editorToken();
    TrackFixture fixture = createPublishedTrack(token);

    assertThat(
            mockMvc.perform(get("/api/v1/manifest/catalog")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(
            mockMvc
                .perform(get("/api/v1/manifest/track/" + fixture.trackId()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  private JsonNode trackManifest(UUID trackId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/manifest/track/" + trackId)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return json(result);
  }

  private static JsonNode catalogRow(JsonNode catalog, UUID trackId) {
    for (JsonNode row : catalog.path("tracks")) {
      if (row.path("track_id").asString().equals(trackId.toString())) {
        return row;
      }
    }
    return null;
  }

  private static JsonNode entityById(JsonNode manifest, UUID entityId) {
    for (JsonNode entity : manifest.path("entities")) {
      if (entity.path("entity_id").asString().equals(entityId.toString())) {
        return entity;
      }
    }
    throw new AssertionError("Manifest does not list entity " + entityId);
  }

  private static List<String> localesOf(JsonNode translations) {
    List<String> locales = new ArrayList<>();
    for (JsonNode translation : translations) {
      locales.add(translation.path("locale").asString());
    }
    return locales;
  }
}
