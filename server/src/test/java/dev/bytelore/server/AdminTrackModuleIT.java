package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.bytelore.server.content.admin.dto.CreateModuleRequest;
import dev.bytelore.server.content.admin.dto.CreateTrackRequest;
import dev.bytelore.server.content.admin.dto.ReorderRequest;
import dev.bytelore.server.content.admin.dto.UpdateModuleRequest;
import dev.bytelore.server.content.admin.dto.UpdateTrackRequest;
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
 * Admin CRUD for tracks and modules (§5.4.2-3): the authorization matrix, optimistic-lock
 * conflicts, order normalization and {@code ORDER_SET_INCOMPLETE}, the delete rules, and the track
 * {@code content_version} bump propagation that every one of these writes must cause.
 */
class AdminTrackModuleIT extends ContentApiTestSupport {

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  @Test
  void authorizationMatrixOnTrackCreation() throws Exception {
    CreateTrackRequest body =
        new CreateTrackRequest(uniqueSlug("auth-matrix"), "Auth Matrix", null, null, null, false);
    String payload = jsonMapper.writeValueAsString(body);

    MvcResult anonymous =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
    assertThat(errorCode(anonymous)).isEqualTo("AUTH_REQUIRED");

    String userToken = userToken();
    MvcResult asUser =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(asUser.getResponse().getStatus()).isEqualTo(403);
    assertThat(errorCode(asUser)).isEqualTo("FORBIDDEN_ROLE");

    String editorToken = editorToken();
    MvcResult asEditor =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + editorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(asEditor.getResponse().getStatus()).isEqualTo(201);
    createdTrackIds.add(UUID.fromString(json(asEditor).path("id").asString()));

    CreateTrackRequest adminBody =
        new CreateTrackRequest(
            uniqueSlug("auth-matrix-admin"), "Auth Matrix Admin", null, null, null, false);
    MvcResult asAdmin =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(adminBody)))
            .andReturn();
    assertThat(asAdmin.getResponse().getStatus()).isEqualTo(201);
    createdTrackIds.add(UUID.fromString(json(asAdmin).path("id").asString()));
  }

  @Test
  void creatingATrackWithADuplicateSlugIsRejected() throws Exception {
    String token = editorToken();
    String slug = uniqueSlug("dup-track");
    UUID trackId = createTrack(token, slug);
    createdTrackIds.add(trackId);

    MvcResult duplicate = createTrackRaw(token, slug);
    assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(duplicate)).isEqualTo("SLUG_ALREADY_EXISTS");
  }

  @Test
  void updatingATrackWithAStaleVersionIsRejectedAndAFreshVersionSucceedsAndBumpsContentVersion()
      throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token, uniqueSlug("versioned-track"));
    createdTrackIds.add(trackId);

    JsonNode initial = json(getTrack(token, trackId));
    long version = initial.path("version").asLong();
    int contentVersion = initial.path("content_version").asInt();

    UpdateTrackRequest stale =
        new UpdateTrackRequest(null, "New Title", null, null, null, null, version);
    // Apply once to advance the version, then reuse the now-stale value.
    MvcResult firstUpdate =
        mockMvc
            .perform(
                patch("/api/v1/admin/tracks/" + trackId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(stale)))
            .andReturn();
    assertThat(firstUpdate.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(firstUpdate).path("content_version").asInt()).isEqualTo(contentVersion + 1);

    MvcResult conflict =
        mockMvc
            .perform(
                patch("/api/v1/admin/tracks/" + trackId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(stale)))
            .andReturn();
    assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(conflict)).isEqualTo("VERSION_CONFLICT");
  }

  @Test
  void deletingATrackWithModulesIsBlocked() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token, uniqueSlug("blocked-delete"));
    createdTrackIds.add(trackId);
    createModule(token, trackId, "Module A", null);

    MvcResult notEmpty =
        mockMvc
            .perform(
                delete("/api/v1/admin/tracks/" + trackId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(notEmpty.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(notEmpty)).isEqualTo("PARENT_NOT_EMPTY");
  }

  @Test
  void deletingAPublishedTrackIsBlocked() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token, uniqueSlug("published-delete"));
    createdTrackIds.add(trackId);

    // No modules on this one: isolates the PUBLISHED_DELETE_BLOCKED rule from PARENT_NOT_EMPTY.
    JsonNode current = json(getTrack(token, trackId));
    UpdateTrackRequest publish =
        new UpdateTrackRequest(
            null, null, null, null, null, true, current.path("version").asLong());
    mockMvc
        .perform(
            patch("/api/v1/admin/tracks/" + trackId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(publish)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

    MvcResult published =
        mockMvc
            .perform(
                delete("/api/v1/admin/tracks/" + trackId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(published.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(published)).isEqualTo("PUBLISHED_DELETE_BLOCKED");
  }

  @Test
  void reorderingModulesNormalizesOrderAndRejectsAnIncompleteSet() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token, uniqueSlug("reorder-modules"));
    createdTrackIds.add(trackId);

    UUID moduleA = createModule(token, trackId, "Module A", null);
    UUID moduleB = createModule(token, trackId, "Module B", null);
    UUID moduleC = createModule(token, trackId, "Module C", null);

    int trackVersionBefore = json(getTrack(token, trackId)).path("content_version").asInt();

    // Incomplete: only two of the three modules.
    MvcResult incomplete =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/modules/order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ReorderRequest(List.of(moduleA, moduleB)))))
            .andReturn();
    assertThat(incomplete.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(incomplete)).isEqualTo("ORDER_SET_INCOMPLETE");

    MvcResult reordered =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/modules/order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ReorderRequest(List.of(moduleC, moduleA, moduleB)))))
            .andReturn();
    assertThat(reordered.getResponse().getStatus()).isEqualTo(200);
    JsonNode reorderedBody = json(reordered);
    assertThat(reorderedBody.get(0).path("id").asString()).isEqualTo(moduleC.toString());
    assertThat(reorderedBody.get(0).path("order").asInt()).isEqualTo(1);
    assertThat(reorderedBody.get(1).path("id").asString()).isEqualTo(moduleA.toString());
    assertThat(reorderedBody.get(1).path("order").asInt()).isEqualTo(2);
    assertThat(reorderedBody.get(2).path("id").asString()).isEqualTo(moduleB.toString());
    assertThat(reorderedBody.get(2).path("order").asInt()).isEqualTo(3);

    int trackVersionAfter = json(getTrack(token, trackId)).path("content_version").asInt();
    assertThat(trackVersionAfter).isGreaterThan(trackVersionBefore);
  }

  @Test
  void movingAModuleToAnotherTrackBumpsBothTracksAndEveryLessonInIt() throws Exception {
    String token = editorToken();
    UUID trackA = createTrack(token, uniqueSlug("move-src"));
    UUID trackB = createTrack(token, uniqueSlug("move-dst"));
    createdTrackIds.add(trackA);
    createdTrackIds.add(trackB);

    UUID moduleId = createModule(token, trackA, "Movable module", null);
    UUID lessonId =
        createLesson(token, moduleId, uniqueSlug("movable-lesson"), "Movable Lesson", "Body one.");

    int trackAVersionBefore = json(getTrack(token, trackA)).path("content_version").asInt();
    int trackBVersionBefore = json(getTrack(token, trackB)).path("content_version").asInt();
    int lessonVersionBefore = json(getLesson(token, lessonId)).path("content_version").asInt();

    // The module group has no standalone GET (§5.4.3); its `version` is learned from the create
    // response and is still 0 here since nothing else has touched it.
    UpdateModuleRequest move = new UpdateModuleRequest(trackB, null, null, null, 0L);
    MvcResult moved =
        mockMvc
            .perform(
                patch("/api/v1/admin/modules/" + moduleId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(move)))
            .andReturn();
    assertThat(moved.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(moved).path("track_id").asString()).isEqualTo(trackB.toString());

    assertThat(json(getTrack(token, trackA)).path("content_version").asInt())
        .isGreaterThan(trackAVersionBefore);
    assertThat(json(getTrack(token, trackB)).path("content_version").asInt())
        .isGreaterThan(trackBVersionBefore);
    assertThat(json(getLesson(token, lessonId)).path("content_version").asInt())
        .isGreaterThan(lessonVersionBefore);
    assertThat(json(getLesson(token, lessonId)).path("module_id").asString())
        .isEqualTo(moduleId.toString());
  }

  // ---- fixtures -----------------------------------------------------------------------------

  private UUID createTrack(String token, String slug) throws Exception {
    MvcResult result = createTrackRaw(token, slug);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(json(result).path("id").asString());
  }

  private MvcResult createTrackRaw(String token, String slug) throws Exception {
    CreateTrackRequest body =
        new CreateTrackRequest(slug, "Track " + slug, "A fixture track.", null, null, false);
    return mockMvc
        .perform(
            post("/api/v1/admin/tracks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
        .andReturn();
  }

  private UUID createModule(String token, UUID trackId, String title, Integer order)
      throws Exception {
    CreateModuleRequest body = new CreateModuleRequest(title, order, null);
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

  private UUID createLesson(String token, UUID moduleId, String slug, String title, String body)
      throws Exception {
    String payload =
        "{\"slug\":\"%s\",\"title\":\"%s\",\"body_markdown\":\"%s\",\"difficulty\":\"BEGINNER\"}"
            .formatted(slug, title, body);
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

  private MvcResult getLesson(String token, UUID lessonId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/admin/lessons/" + lessonId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andReturn();
  }
}
