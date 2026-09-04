package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.devatlas.server.content.admin.dto.CreateCodeExampleRequest;
import dev.devatlas.server.content.admin.dto.CreateModuleRequest;
import dev.devatlas.server.content.admin.dto.CreateTrackRequest;
import dev.devatlas.server.content.admin.dto.ReorderRequest;
import dev.devatlas.server.content.admin.dto.UpdateCodeExampleRequest;
import dev.devatlas.server.content.admin.dto.UpdateLessonRequest;
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
 * Admin CRUD for lessons and code examples (§5.4.4-5): version bumping (including propagation to
 * the owning track), order normalization, soft delete, sanitization at the write boundary, and slug
 * reuse after a soft delete.
 */
class AdminLessonCodeExampleIT extends ContentApiTestSupport {

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  @Test
  void creatingALessonSanitizesAScriptTagAndAnOnErrorAttribute() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    String slug = uniqueSlug("sanitized-lesson");
    String maliciousBody =
        "## Heading\\n\\n<script>alert(1)</script><img src=x onerror=\\\"alert(2)\\\">Safe text remains.";
    String payload =
        "{\"slug\":\"%s\",\"title\":\"Sanitized Lesson\",\"body_markdown\":\"%s\",\"difficulty\":\"BEGINNER\"}"
            .formatted(slug, maliciousBody);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    String storedBody = json(result).path("body_markdown").asString();
    assertThat(storedBody)
        .doesNotContain("<script")
        .doesNotContain("onerror")
        .contains("Safe text remains.");
  }

  @Test
  void aBodyThatSanitizesToNothingIsRejected() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    String payload =
        "{\"slug\":\"%s\",\"title\":\"Empty After Sanitize\",\"body_markdown\":\"<script>alert(1)</script>\",\"difficulty\":\"BEGINNER\"}"
            .formatted(uniqueSlug("empty-sanitized"));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("SANITIZED_CONTENT_EMPTY");
  }

  @Test
  void softDeletingALessonHidesItAndFreesItsSlugForReuse() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);
    String slug = uniqueSlug("soft-delete-lesson");
    UUID lessonId = createLesson(token, moduleId, slug, "Deletable Lesson");

    MvcResult deleted =
        mockMvc
            .perform(
                delete("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(deleted.getResponse().getStatus()).isEqualTo(204);

    MvcResult afterDelete =
        mockMvc
            .perform(
                get("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(afterDelete.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(afterDelete)).isEqualTo("LESSON_NOT_FOUND");

    // The freed slug can be reused by a brand new lesson.
    UUID recreatedId = createLesson(token, moduleId, slug, "Recreated Lesson");
    assertThat(recreatedId).isNotEqualTo(lessonId);
  }

  @Test
  void updatingALessonBumpsItsVersionAndTheOwningTracksVersion() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);
    UUID trackId = lastCreatedTrackId();
    UUID lessonId = createLesson(token, moduleId, uniqueSlug("bump-lesson"), "Bump Lesson");

    int trackVersionBefore = json(getTrack(token, trackId)).path("content_version").asInt();
    JsonNode lessonBefore = json(getLesson(token, lessonId));
    int lessonVersionBefore = lessonBefore.path("content_version").asInt();
    long optimisticVersion = lessonBefore.path("version").asLong();

    UpdateLessonRequest update =
        new UpdateLessonRequest(
            null, "Updated Title", null, null, null, null, null, optimisticVersion);
    MvcResult updated =
        mockMvc
            .perform(
                patch("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(update)))
            .andReturn();
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(updated).path("content_version").asInt()).isGreaterThan(lessonVersionBefore);

    int trackVersionAfter = json(getTrack(token, trackId)).path("content_version").asInt();
    assertThat(trackVersionAfter).isGreaterThan(trackVersionBefore);
  }

  @Test
  void codeExampleLifecycleBumpsTheLessonAndRejectsAnUnsupportedLanguage() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);
    UUID lessonId =
        createLesson(token, moduleId, uniqueSlug("code-example-lesson"), "Code Example Lesson");

    int lessonVersionBefore = json(getLesson(token, lessonId)).path("content_version").asInt();

    CreateCodeExampleRequest unsupported =
        new CreateCodeExampleRequest("brainfuck", "++++", null, null);
    MvcResult rejected =
        mockMvc
            .perform(
                post("/api/v1/admin/lessons/" + lessonId + "/code-examples")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(unsupported)))
            .andReturn();
    assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(rejected)).isEqualTo("UNSUPPORTED_LANGUAGE");

    CreateCodeExampleRequest create =
        new CreateCodeExampleRequest("typescript", "const x = 1;", "caption", null);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/lessons/" + lessonId + "/code-examples")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(create)))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    UUID codeExampleId = UUID.fromString(json(created).path("id").asString());
    int lessonVersionAfterCreate = json(created).path("lesson_content_version").asInt();
    assertThat(lessonVersionAfterCreate).isGreaterThan(lessonVersionBefore);
    assertThat(json(getLesson(token, lessonId)).path("content_version").asInt())
        .isEqualTo(lessonVersionAfterCreate);

    long codeExampleVersion = json(created).path("version").asLong();
    UpdateCodeExampleRequest update =
        new UpdateCodeExampleRequest(null, "const x = 2;", null, null, codeExampleVersion);
    MvcResult updated =
        mockMvc
            .perform(
                patch("/api/v1/admin/code-examples/" + codeExampleId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(update)))
            .andReturn();
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(updated).path("lesson_content_version").asInt())
        .isGreaterThan(lessonVersionAfterCreate);

    MvcResult deleted =
        mockMvc
            .perform(
                delete("/api/v1/admin/code-examples/" + codeExampleId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
    assertThat(json(getLesson(token, lessonId)).path("code_examples")).isEmpty();
  }

  @Test
  void reorderingCodeExamplesRejectsAnIncompleteSetAndAppliesACompleteOne() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);
    UUID lessonId =
        createLesson(
            token, moduleId, uniqueSlug("reorder-examples-lesson"), "Reorder Examples Lesson");

    UUID exampleA = createCodeExample(token, lessonId, "typescript", "const a = 1;");
    UUID exampleB = createCodeExample(token, lessonId, "typescript", "const b = 2;");

    MvcResult incomplete =
        mockMvc
            .perform(
                put("/api/v1/admin/lessons/" + lessonId + "/code-examples/order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new ReorderRequest(List.of(exampleA)))))
            .andReturn();
    assertThat(incomplete.getResponse().getStatus()).isEqualTo(409);
    assertThat(errorCode(incomplete)).isEqualTo("ORDER_SET_INCOMPLETE");

    MvcResult reordered =
        mockMvc
            .perform(
                put("/api/v1/admin/lessons/" + lessonId + "/code-examples/order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new ReorderRequest(List.of(exampleB, exampleA)))))
            .andReturn();
    assertThat(reordered.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(reordered);
    assertThat(body.get(0).path("id").asString()).isEqualTo(exampleB.toString());
    assertThat(body.get(0).path("order").asInt()).isEqualTo(1);
    assertThat(body.get(1).path("id").asString()).isEqualTo(exampleA.toString());
    assertThat(body.get(1).path("order").asInt()).isEqualTo(2);
  }

  // ---- fixtures -----------------------------------------------------------------------------

  private UUID trackIdFixture;

  private UUID createTrackAndModule(String token) throws Exception {
    CreateTrackRequest trackBody =
        new CreateTrackRequest(
            uniqueSlug("lesson-fixture-track"), "Lesson Fixture Track", null, null, null, false);
    MvcResult trackResult =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(trackBody)))
            .andReturn();
    assertThat(trackResult.getResponse().getStatus()).isEqualTo(201);
    UUID trackId = UUID.fromString(json(trackResult).path("id").asString());
    createdTrackIds.add(trackId);
    trackIdFixture = trackId;

    CreateModuleRequest moduleBody = new CreateModuleRequest("Fixture Module", null, null);
    MvcResult moduleResult =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks/" + trackId + "/modules")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(moduleBody)))
            .andReturn();
    assertThat(moduleResult.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(json(moduleResult).path("id").asString());
  }

  private UUID lastCreatedTrackId() {
    return trackIdFixture;
  }

  private UUID createLesson(String token, UUID moduleId, String slug, String title)
      throws Exception {
    String payload =
        "{\"slug\":\"%s\",\"title\":\"%s\",\"body_markdown\":\"Body content.\",\"difficulty\":\"BEGINNER\"}"
            .formatted(slug, title);
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

  private UUID createCodeExample(String token, UUID lessonId, String language, String code)
      throws Exception {
    CreateCodeExampleRequest body = new CreateCodeExampleRequest(language, code, null, null);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/lessons/" + lessonId + "/code-examples")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
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
