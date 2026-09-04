package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.devatlas.server.content.admin.dto.CreateModuleRequest;
import dev.devatlas.server.content.admin.dto.CreateTrackRequest;
import dev.devatlas.server.translation.dto.TranslationUpsertRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Translation read/write (§5.6, §7.7): validation rules, fallback bookkeeping, and version bumps.
 */
class TranslationIT extends ContentApiTestSupport {

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  @Test
  void mindMapIsRejectedAsAnUnsupportedTranslatableEntityType() throws Exception {
    String token = editorToken();
    TranslationUpsertRequest body = new TranslationUpsertRequest("Baslik", null, null);

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/MIND_MAP/" + UUID.randomUUID() + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("ENTITY_TYPE_UNSUPPORTED");
  }

  @Test
  void aGarbageEntityTypeIsAlsoRejectedAsUnsupported() throws Exception {
    String token = editorToken();
    TranslationUpsertRequest body = new TranslationUpsertRequest("Baslik", null, null);

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/NOT_A_TYPE/" + UUID.randomUUID() + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("ENTITY_TYPE_UNSUPPORTED");
  }

  @Test
  void englishIsRejectedAsATranslationLocale() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);
    TranslationUpsertRequest body = new TranslationUpsertRequest("English Again", "Body", null);

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/TRACK/" + trackId + "/en")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("CANONICAL_LOCALE_NOT_ALLOWED");
  }

  @Test
  void aLessonTranslationWithNoBodyIsRejectedWithAFieldError() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);
    UUID moduleId = createModule(token, trackId);
    UUID lessonId = createLesson(token, moduleId);

    TranslationUpsertRequest missingBody = new TranslationUpsertRequest("Baslik", null, null);
    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/LESSON/" + lessonId + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(missingBody)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("VALIDATION_FAILED");
    assertThat(json(result).path("errors").get(0).path("field").asString()).isEqualTo("body");
  }

  @Test
  void aTrackTranslationWithNoBodyIsAcceptedSinceDescriptionIsOptional() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    TranslationUpsertRequest titleOnly = new TranslationUpsertRequest("Sadece Baslik", null, null);
    MvcResult created =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/TRACK/" + trackId + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(titleOnly)))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(created).path("body").isNull()).isTrue();
  }

  @Test
  void writingATrackTranslationBumpsTheTracksContentVersionAndIsReadableWithFallbackBookkeeping()
      throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    int versionBefore = json(getTrack(token, trackId)).path("content_version").asInt();

    TranslationUpsertRequest create =
        new TranslationUpsertRequest("Turkce Baslik", "Turkce aciklama.", null);
    MvcResult createdResult =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/TRACK/" + trackId + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(create)))
            .andReturn();
    assertThat(createdResult.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(createdResult).path("content_version").asInt()).isGreaterThan(versionBefore);
    assertThat(json(getTrack(token, trackId)).path("content_version").asInt())
        .isGreaterThan(versionBefore);

    // Updating without `version` fails; updating with a stale value conflicts.
    TranslationUpsertRequest noVersion =
        new TranslationUpsertRequest("Yeni Baslik", "Yeni aciklama.", null);
    MvcResult missingVersion =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/TRACK/" + trackId + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(noVersion)))
            .andReturn();
    assertThat(missingVersion.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(missingVersion)).isEqualTo("VALIDATION_FAILED");

    // GET returns canonical English plus the stored translation, with `fr`/`de` still missing.
    MvcResult get =
        mockMvc
            .perform(
                get("/api/v1/admin/translations/TRACK/" + trackId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(get.getResponse().getStatus()).isEqualTo(200);
    JsonNode getBody = json(get);
    assertThat(getBody.path("canonical").path("locale").asString()).isEqualTo("en");
    assertThat(getBody.path("translations")).hasSize(1);
    assertThat(getBody.path("translations").get(0).path("locale").asString()).isEqualTo("tr");
    List<String> missing = new ArrayList<>();
    getBody.path("missing_locales").forEach(node -> missing.add(node.asString()));
    assertThat(missing).containsExactlyInAnyOrder("fr", "de");

    // The public read endpoint reports is_fallback=false for `tr` now, and true for `fr`.
    MvcResult publicTr =
        mockMvc
            .perform(get("/api/v1/tracks/" + trackSlug(token, trackId) + "?locale=tr"))
            .andReturn();
    assertThat(json(publicTr).path("is_fallback").asBoolean()).isFalse();
    assertThat(json(publicTr).path("locale").asString()).isEqualTo("tr");

    MvcResult publicFr =
        mockMvc
            .perform(get("/api/v1/tracks/" + trackSlug(token, trackId) + "?locale=fr"))
            .andReturn();
    assertThat(json(publicFr).path("is_fallback").asBoolean()).isTrue();
    assertThat(json(publicFr).path("locale").asString()).isEqualTo("en");

    // Deleting the translation restores the English fallback.
    MvcResult deleted =
        mockMvc
            .perform(
                delete("/api/v1/admin/translations/TRACK/" + trackId + "/tr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(deleted.getResponse().getStatus()).isEqualTo(204);

    MvcResult afterDelete =
        mockMvc
            .perform(get("/api/v1/tracks/" + trackSlug(token, trackId) + "?locale=tr"))
            .andReturn();
    assertThat(json(afterDelete).path("is_fallback").asBoolean()).isTrue();
  }

  @Test
  void anUnsupportedLocaleIsRejected() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);
    TranslationUpsertRequest body = new TranslationUpsertRequest("Title", null, null);

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/translations/TRACK/" + trackId + "/xx")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("UNSUPPORTED_LOCALE");
  }

  private UUID createTrack(String token) throws Exception {
    CreateTrackRequest body =
        new CreateTrackRequest(
            uniqueSlug("translation-track"),
            "Translation Track",
            "English description.",
            null,
            null,
            true);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    UUID trackId = UUID.fromString(json(result).path("id").asString());
    createdTrackIds.add(trackId);
    return trackId;
  }

  private String trackSlug(String token, UUID trackId) throws Exception {
    return json(getTrack(token, trackId)).path("slug").asString();
  }

  private UUID createModule(String token, UUID trackId) throws Exception {
    CreateModuleRequest body = new CreateModuleRequest("Fixture Module", null, null);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks/" + trackId + "/modules")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(json(result).path("id").asString());
  }

  private UUID createLesson(String token, UUID moduleId) throws Exception {
    String payload =
        "{\"slug\":\"%s\",\"title\":\"Fixture Lesson\",\"body_markdown\":\"English body.\",\"difficulty\":\"BEGINNER\"}"
            .formatted(uniqueSlug("translation-lesson"));
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

  private MvcResult getTrack(String token, UUID trackId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/admin/tracks/" + trackId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andReturn();
  }
}
