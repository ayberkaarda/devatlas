package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.bytelore.server.content.admin.dto.CreateModuleRequest;
import dev.bytelore.server.content.admin.dto.CreateTrackRequest;
import dev.bytelore.server.content.admin.dto.MindMapNodeRequest;
import dev.bytelore.server.content.admin.dto.MindMapUpsertRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Admin mind map upsert/get/delete (§5.4.6, §5.2.4, §7.6). */
class AdminMindMapIT extends ContentApiTestSupport {

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  @Test
  void firstWriteCreatesTheMapAndASecondWriteRequiresVersionAndBumpsTrack() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    int trackVersionBefore = json(getTrack(token, trackId)).path("content_version").asInt();

    MindMapNodeRequest root =
        new MindMapNodeRequest(
            "root",
            "Root",
            null,
            List.of(new MindMapNodeRequest("child", "Child", null, List.of())));
    MvcResult created =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null))))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(created).path("content_version").asInt()).isEqualTo(1);
    assertThat(json(getTrack(token, trackId)).path("content_version").asInt())
        .isGreaterThan(trackVersionBefore);

    // A second write without `version` is rejected.
    MvcResult missingVersion =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null))))
            .andReturn();
    assertThat(missingVersion.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(missingVersion)).isEqualTo("VALIDATION_FAILED");

    long currentVersion = json(created).path("version").asLong();
    MindMapNodeRequest updatedRoot =
        new MindMapNodeRequest("root", "Root Updated", null, List.of());
    int mapVersionBefore = json(created).path("content_version").asInt();
    MvcResult updated =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new MindMapUpsertRequest(updatedRoot, currentVersion))))
            .andReturn();
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(updated).path("content_version").asInt()).isGreaterThan(mapVersionBefore);

    // A stale version is a conflict.
    MvcResult stale =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new MindMapUpsertRequest(updatedRoot, currentVersion))))
            .andReturn();
    assertThat(stale.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(stale)).isEqualTo("VERSION_CONFLICT");

    MvcResult fetched =
        mockMvc
            .perform(
                get("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(fetched).path("root").path("label").asString()).isEqualTo("Root Updated");
  }

  @Test
  void duplicateNodeIdsAndOutOfTrackLessonReferencesAreRejectedAsMindMapInvalid() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    MindMapNodeRequest duplicateIds =
        new MindMapNodeRequest(
            "root",
            "Root",
            null,
            List.of(
                new MindMapNodeRequest("dup", "One", null, List.of()),
                new MindMapNodeRequest("dup", "Two", null, List.of())));
    MvcResult duplicateResult =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new MindMapUpsertRequest(duplicateIds, null))))
            .andReturn();
    assertThat(duplicateResult.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(duplicateResult)).isEqualTo("MIND_MAP_INVALID");

    UUID foreignLessonId = UUID.randomUUID();
    MindMapNodeRequest outsideLesson =
        new MindMapNodeRequest(
            "root",
            "Root",
            null,
            List.of(new MindMapNodeRequest("leaf", "Leaf", foreignLessonId, List.of())));
    MvcResult outsideResult =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new MindMapUpsertRequest(outsideLesson, null))))
            .andReturn();
    assertThat(outsideResult.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(outsideResult)).isEqualTo("MIND_MAP_INVALID");
  }

  @Test
  void aLessonWithinTheSameTrackIsAcceptedAsAMindMapNodeReference() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);
    CreateModuleRequest moduleBody = new CreateModuleRequest("Module", null, null);
    MvcResult moduleResult =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks/" + trackId + "/modules")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(moduleBody)))
            .andReturn();
    UUID moduleId = UUID.fromString(json(moduleResult).path("id").asString());

    String lessonPayload =
        "{\"slug\":\"%s\",\"title\":\"Fixture Lesson\",\"body_markdown\":\"Body.\",\"difficulty\":\"BEGINNER\"}"
            .formatted(uniqueSlug("mindmap-lesson"));
    MvcResult lessonResult =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(lessonPayload))
            .andReturn();
    UUID lessonId = UUID.fromString(json(lessonResult).path("id").asString());

    MindMapNodeRequest root =
        new MindMapNodeRequest(
            "root",
            "Root",
            null,
            List.of(new MindMapNodeRequest("leaf", "Leaf", lessonId, List.of())));
    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);

    MvcResult deleted =
        mockMvc
            .perform(
                delete("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(deleted.getResponse().getStatus()).isEqualTo(204);

    MvcResult afterDelete =
        mockMvc
            .perform(
                get("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(afterDelete.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(afterDelete)).isEqualTo("MIND_MAP_NOT_FOUND");
  }

  /**
   * A node label is stored exactly as it was written. The fixture is the label that used to be
   * corrupted -- an equals sign came back as {@code &#61;} -- alongside the other characters an
   * HTML serializer rewrites, so a regression shows up as an inequality rather than as a rendering
   * bug somebody notices months later.
   */
  @Test
  void nodeLabelsAreStoredExactlyAsWritten() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    MindMapNodeRequest root =
        new MindMapNodeRequest(
            "root",
            "x = y",
            null,
            List.of(
                new MindMapNodeRequest("cmp", "a < b & b > a", null, List.of()),
                new MindMapNodeRequest("quote", "O'Reilly's \"Java\"", null, List.of()),
                new MindMapNodeRequest("mail", "ping me@example.test", null, List.of())));

    MvcResult created =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null))))
            .andReturn();

    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    JsonNode storedRoot = json(created).path("root");
    assertThat(storedRoot.path("label").asString()).isEqualTo("x = y");
    assertThat(storedRoot.path("children").get(0).path("label").asString())
        .isEqualTo("a < b & b > a");
    assertThat(storedRoot.path("children").get(1).path("label").asString())
        .isEqualTo("O'Reilly's \"Java\"");
    assertThat(storedRoot.path("children").get(2).path("label").asString())
        .isEqualTo("ping me@example.test");
  }

  @Test
  void aNodeLabelThatCarriesMarkupIsRefusedWithTheElementNamed() throws Exception {
    String token = editorToken();
    UUID trackId = createTrack(token);

    MindMapNodeRequest root =
        new MindMapNodeRequest(
            "root",
            "Root",
            null,
            List.of(new MindMapNodeRequest("bad", "<b>bold</b> label", null, List.of())));

    MvcResult result =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null))))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("UNSAFE_HTML");
    assertThat(json(result).path("message").asString()).contains("<b>").contains("'bad'");
  }

  @Test
  void authorizationMatrixOnMindMapUpsert() throws Exception {
    String editorForFixture = editorToken();
    UUID trackId = createTrack(editorForFixture);
    MindMapNodeRequest root = new MindMapNodeRequest("root", "Root", null, List.of());
    String payload = jsonMapper.writeValueAsString(new MindMapUpsertRequest(root, null));

    MvcResult anonymous =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);

    String userToken = userToken();
    MvcResult asUser =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(asUser.getResponse().getStatus()).isEqualTo(403);
    assertThat(errorCode(asUser)).isEqualTo("FORBIDDEN_ROLE");
  }

  private UUID createTrack(String token) throws Exception {
    CreateTrackRequest body =
        new CreateTrackRequest(
            uniqueSlug("mindmap-track"), "Mind Map Track", null, null, null, false);
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

  private MvcResult getTrack(String token, UUID trackId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/admin/tracks/" + trackId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andReturn();
  }
}
