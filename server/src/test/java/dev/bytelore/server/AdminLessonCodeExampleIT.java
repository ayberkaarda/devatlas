package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.bytelore.server.content.admin.dto.CreateCodeExampleRequest;
import dev.bytelore.server.content.admin.dto.CreateLessonRequest;
import dev.bytelore.server.content.admin.dto.CreateModuleRequest;
import dev.bytelore.server.content.admin.dto.CreateTrackRequest;
import dev.bytelore.server.content.admin.dto.ReorderRequest;
import dev.bytelore.server.content.admin.dto.UpdateCodeExampleRequest;
import dev.bytelore.server.content.admin.dto.UpdateLessonRequest;
import dev.bytelore.server.domain.Difficulty;
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

  /**
   * The bug this test exists to prevent: a body that came back different from the one that was
   * sent. Every character in the fixture was measured to be rewritten as an HTML entity when the
   * markdown source was run through an HTML sanitizer, which is what the write boundary used to do
   * -- a code fence, an apostrophe, an e-mail address and a blockquote could not survive a save.
   * The comparison is against the exact string that was submitted, through both the write response
   * and a fresh read, because the stored bytes are also the hashed and shipped bytes.
   */
  @Test
  void aBodyOfCodeFencesQuotesAndPunctuationRoundTripsByteForByte() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    String body =
        """
        # Comparing values

        Don't use `==` when you mean `===`; a < b and b > a are both fine.

        ```ts
        const answer: number = 42;
        const ok = a === b && c !== d;
        ```

        > Quoted advice: "measure it" -- mail me@example.com if it differs.

        Inline `x = y + 1`, a stray & ampersand and a + sign.

        ---

        Allowed inline HTML: <b>bold</b> and <details><summary>Show</summary>

        the answer

        </details>
        """;

    CreateLessonRequest request =
        new CreateLessonRequest(
            uniqueSlug("round-trip"), "Round Trip", body, Difficulty.BEGINNER, null, null);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();

    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(created).path("body_markdown").asString()).isEqualTo(body);

    UUID lessonId = UUID.fromString(json(created).path("id").asString());
    MvcResult read =
        mockMvc
            .perform(
                get("/api/v1/admin/lessons/" + lessonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertThat(read.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(read).path("body_markdown").asString()).isEqualTo(body);
  }

  @Test
  void aBodyWritingCrlfLineEndingsIsStoredAsLfAndOtherwiseUnchanged() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    String crlf = "# Title\r\n\r\n```ts\r\nconst x = 1;\r\n```\r\n";
    String expected = "# Title\n\n```ts\nconst x = 1;\n```\n";

    CreateLessonRequest request =
        new CreateLessonRequest(
            uniqueSlug("crlf-body"), "CRLF Body", crlf, Difficulty.BEGINNER, null, null);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();

    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(created).path("body_markdown").asString()).isEqualTo(expected);
  }

  @Test
  void aBodyCarryingAScriptTagIsRefusedAndTheElementIsNamed() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    CreateLessonRequest request =
        new CreateLessonRequest(
            uniqueSlug("script-lesson"),
            "Script Lesson",
            "## Heading\n\n<script>alert(1)</script><img src=x onerror=\"alert(2)\">Safe text.",
            Difficulty.BEGINNER,
            null,
            null);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("UNSAFE_HTML");
    // The author has to be able to tell what to remove, so the message names it.
    assertThat(json(result).path("message").asString())
        .contains("<script>")
        .contains("img[onerror]");
  }

  @Test
  void aMarkdownLinkToAJavascriptUrlIsRefused() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    CreateLessonRequest request =
        new CreateLessonRequest(
            uniqueSlug("js-link-lesson"),
            "Javascript Link",
            "Read [the docs](javascript:alert(1)) for more.",
            Difficulty.BEGINNER,
            null,
            null);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorCode(result)).isEqualTo("UNSAFE_HTML");
  }

  @Test
  void aScriptTagInsideACodeFenceIsAcceptedAndStoredVerbatim() throws Exception {
    String token = editorToken();
    UUID moduleId = createTrackAndModule(token);

    String body = "Never write this:\n\n```html\n<script>alert(1)</script>\n```\n";
    CreateLessonRequest request =
        new CreateLessonRequest(
            uniqueSlug("fenced-script"), "Fenced Script", body, Difficulty.BEGINNER, null, null);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(result).path("body_markdown").asString()).isEqualTo(body);
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
        new CreateCodeExampleRequest("cpp", "int main() { return 0; }", "caption", null);
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
