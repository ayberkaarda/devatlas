package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import dev.devatlas.server.content.admin.dto.CreateCodeExampleRequest;
import dev.devatlas.server.content.admin.dto.CreateModuleRequest;
import dev.devatlas.server.content.admin.dto.CreateTrackRequest;
import dev.devatlas.server.content.admin.dto.MindMapNodeRequest;
import dev.devatlas.server.content.admin.dto.MindMapUpsertRequest;
import dev.devatlas.server.content.packaging.LessonPackage;
import dev.devatlas.server.content.packaging.MindMapPackage;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The verification §5.4.1 of the REST contract demands, not merely the documentation of it (and the
 * same table appears as the content sync protocol's §3.5 "every field present in a package
 * document" test): this test reflects over {@link LessonPackage} and {@link MindMapPackage} -- the
 * exact shapes {@code ContentVersionService} hashes -- and asserts that every mutable field has a
 * registered mutation scenario proving it bumps {@code content_version} and changes the stored
 * digest. A field added to either package later, with no scenario registered here, fails this
 * test's own bookkeeping assertion before it ever gets the chance to fail silently in production.
 *
 * <p>{@code entityId} and {@code entityType} (both packages) and {@code trackId} ({@link
 * MindMapPackage}) are excluded: they are identity, fixed at creation, and no endpoint in this
 * contract ever changes them, so there is no mutation scenario that could exist for them. {@code
 * contentVersion} is excluded from the coverage requirement for a different reason -- it is not an
 * independently settable field, it is the counter every other bump increments -- even though, per
 * content sync protocol §3.3/§3.5, its value is very much part of the hashed bytes.
 *
 * <p>The {@code translations} scenario below is also the content sync protocol's explicitly
 * required "adding a translation" test (§3.5: "Digest changes <strong>and</strong> content_version
 * increments"): translations are packaged inside the lesson, not alongside it, so writing one must
 * move both.
 */
class ContentVersionBumpCoverageIT extends ContentApiTestSupport {

  @Autowired private LessonRepository lessons;
  @Autowired private MindMapRepository mindMaps;

  private final List<UUID> createdTrackIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (UUID trackId : createdTrackIds) {
      deleteTrackTree(trackId);
    }
    createdTrackIds.clear();
  }

  @Test
  void everyIndependentlySettableLessonPackageFieldHasARegisteredBumpScenario() throws Exception {
    Set<String> declaredFields =
        Arrays.stream(LessonPackage.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
    Set<String> needingCoverage = new HashSet<>(declaredFields);
    needingCoverage.removeAll(Set.of("entityId", "entityType", "contentVersion"));

    String token = editorToken();
    UUID trackId = createTrack(token);
    UUID moduleA = createModule(token, trackId, "Module A");
    UUID moduleB = createModule(token, trackId, "Module B");
    UUID lessonId =
        createLesson(token, moduleA, uniqueSlug("bump-cov"), "Coverage Lesson", "Original body.");
    // A sibling to swap positions with, so the `order` scenario has something to move relative to.
    createLesson(token, moduleA, uniqueSlug("bump-cov-sibling"), "Sibling Lesson", "Sibling body.");

    Map<String, Scenario> scenarios = new LinkedHashMap<>();
    scenarios.put(
        "slug",
        () -> patchLesson(token, lessonId, Map.of("slug", uniqueSlug("bump-cov-new-slug"))));
    scenarios.put("title", () -> patchLesson(token, lessonId, Map.of("title", "Updated Title")));
    scenarios.put(
        "bodyMarkdown",
        () -> patchLesson(token, lessonId, Map.of("body_markdown", "Updated body.")));
    scenarios.put(
        "difficulty", () -> patchLesson(token, lessonId, Map.of("difficulty", "ADVANCED")));
    scenarios.put(
        "estimatedMinutes", () -> patchLesson(token, lessonId, Map.of("estimated_minutes", 42)));
    scenarios.put("order", () -> patchLesson(token, lessonId, Map.of("order", 2)));
    scenarios.put(
        "moduleId", () -> patchLesson(token, lessonId, Map.of("module_id", moduleB.toString())));
    scenarios.put(
        "codeExamples",
        () -> {
          CreateCodeExampleRequest body =
              new CreateCodeExampleRequest("typescript", "const x = 1;", null, null);
          mockMvc
              .perform(
                  post("/api/v1/admin/lessons/" + lessonId + "/code-examples")
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(jsonMapper.writeValueAsString(body)))
              .andExpect(
                  org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                      .isCreated());
        });
    scenarios.put(
        "translations",
        () -> {
          // content sync protocol §3.5's explicitly required test: adding a translation changes
          // this lesson's digest (translations are packaged inside it) and bumps content_version.
          String body = "{\"title\":\"Baslik\",\"body\":\"Govde metni.\"}";
          mockMvc
              .perform(
                  put("/api/v1/admin/translations/LESSON/" + lessonId + "/tr")
                      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(
                  org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                      .isCreated());
        });

    assertThat(scenarios.keySet())
        .as("every mutable LessonPackage field must have a registered bump scenario")
        .containsExactlyInAnyOrderElementsOf(needingCoverage);

    for (Map.Entry<String, Scenario> entry : scenarios.entrySet()) {
      Lesson before = lessons.findById(lessonId).orElseThrow();
      int versionBefore = before.getContentVersion();
      String hashBefore = before.getSha256();

      entry.getValue().run();

      Lesson after = lessons.findById(lessonId).orElseThrow();
      assertThat(after.getContentVersion())
          .as("content_version must increase after mutating '%s'", entry.getKey())
          .isGreaterThan(versionBefore);
      assertThat(after.getSha256())
          .as("the stored digest must change after mutating '%s'", entry.getKey())
          .isNotEqualTo(hashBefore);
    }
  }

  @Test
  void theOnlyMutableMindMapPackageFieldIsRootAndItHasABumpScenario() throws Exception {
    Set<String> declaredFields =
        Arrays.stream(MindMapPackage.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
    Set<String> needingCoverage = new HashSet<>(declaredFields);
    needingCoverage.removeAll(Set.of("entityId", "entityType", "contentVersion", "trackId"));
    assertThat(needingCoverage).containsExactly("root");

    String token = editorToken();
    UUID trackId = createTrack(token);
    MindMapNodeRequest initialRoot = new MindMapNodeRequest("root", "Root", null, List.of());
    var created =
        mockMvc
            .perform(
                put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(new MindMapUpsertRequest(initialRoot, null))))
            .andReturn();
    UUID mindMapId = UUID.fromString(json(created).path("id").asString());

    MindMap before = mindMaps.findById(mindMapId).orElseThrow();
    int versionBefore = before.getContentVersion();
    String hashBefore = before.getSha256();

    MindMapNodeRequest updatedRoot =
        new MindMapNodeRequest("root", "Root Renamed", null, List.of());
    long currentVersion = json(created).path("version").asLong();
    mockMvc
        .perform(
            put("/api/v1/admin/tracks/" + trackId + "/mindmap")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new MindMapUpsertRequest(updatedRoot, currentVersion))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

    MindMap after = mindMaps.findById(mindMapId).orElseThrow();
    assertThat(after.getContentVersion()).isGreaterThan(versionBefore);
    assertThat(after.getSha256()).isNotEqualTo(hashBefore);
  }

  @FunctionalInterface
  private interface Scenario {
    void run() throws Exception;
  }

  private void patchLesson(String token, UUID lessonId, Map<String, Object> fields)
      throws Exception {
    long currentVersion = lessons.findById(lessonId).orElseThrow().getVersion();
    Map<String, Object> body = new HashMap<>(fields);
    body.put("version", currentVersion);
    mockMvc
        .perform(
            patch("/api/v1/admin/lessons/" + lessonId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  }

  private UUID createTrack(String token) throws Exception {
    CreateTrackRequest body =
        new CreateTrackRequest(
            uniqueSlug("bump-cov-track"), "Bump Coverage Track", null, null, null, false);
    var result =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    UUID trackId = UUID.fromString(json(result).path("id").asString());
    createdTrackIds.add(trackId);
    return trackId;
  }

  private UUID createModule(String token, UUID trackId, String title) throws Exception {
    CreateModuleRequest body = new CreateModuleRequest(title, null, null);
    var result =
        mockMvc
            .perform(
                post("/api/v1/admin/tracks/" + trackId + "/modules")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(body)))
            .andReturn();
    return UUID.fromString(json(result).path("id").asString());
  }

  private UUID createLesson(String token, UUID moduleId, String slug, String title, String body)
      throws Exception {
    String payload =
        "{\"slug\":\"%s\",\"title\":\"%s\",\"body_markdown\":\"%s\",\"difficulty\":\"BEGINNER\"}"
            .formatted(slug, title, body);
    var result =
        mockMvc
            .perform(
                post("/api/v1/admin/modules/" + moduleId + "/lessons")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    return UUID.fromString(json(result).path("id").asString());
  }
}
