package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Public content read (§5.2): anonymous access, published-only visibility, locale resolution and
 * the {@code is_fallback} bookkeeping, against the fixed seed content of {@code
 * V5__seed_content.sql} (the published {@code angular-path} track, its lessons, and its mind map).
 */
class PublicContentReadIT extends ContentApiTestSupport {

  private static final String SEED_TRACK_SLUG = "angular-path";
  private static final String SEED_LESSON_SLUG = "signals-and-reactivity";

  @Test
  void tracksListReturnsThePublishedSeedTrackAnonymously() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en");
    // The CORS filter contributes its own Vary entries (Origin, ...); assert ours is present among
    // them rather than that Vary carries only Accept-Language.
    assertThat(result.getResponse().getHeaders(HttpHeaders.VARY))
        .contains(HttpHeaders.ACCEPT_LANGUAGE);

    JsonNode body = json(result);
    assertThat(body.path("items")).isNotEmpty();
    boolean found = false;
    for (JsonNode item : body.path("items")) {
      if (SEED_TRACK_SLUG.equals(item.path("slug").asString())) {
        found = true;
        assertThat(item.path("locale").asString()).isEqualTo("en");
        assertThat(item.path("is_fallback").asBoolean()).isFalse();
        assertThat(item.path("module_count").asInt()).isEqualTo(2);
        assertThat(item.path("lesson_count").asInt()).isEqualTo(4);
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void trackDetailReturnsModulesAndLessonsWithoutBodies() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks/" + SEED_TRACK_SLUG)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("slug").asString()).isEqualTo(SEED_TRACK_SLUG);
    assertThat(body.path("has_mind_map").asBoolean()).isTrue();
    assertThat(body.path("modules")).hasSize(2);
    JsonNode firstModule = body.path("modules").get(0);
    assertThat(firstModule.path("lessons")).isNotEmpty();
    assertThat(firstModule.path("lessons").get(0).has("body_markdown")).isFalse();
  }

  @Test
  void unknownTrackSlugIs404() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks/does-not-exist-track")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(result)).isEqualTo("TRACK_NOT_FOUND");
  }

  @Test
  void lessonReadReturnsBodyAndCodeExamplesAndNullProgressForAnonymousCallers() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/lessons/" + SEED_LESSON_SLUG)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("slug").asString()).isEqualTo(SEED_LESSON_SLUG);
    assertThat(body.path("body_markdown").asString()).contains("signal");
    assertThat(body.path("code_examples").isArray()).isTrue();
    assertThat(body.path("progress").isNull()).isTrue();
    assertThat(body.path("track").path("slug").asString()).isEqualTo(SEED_TRACK_SLUG);
  }

  @Test
  void unknownLessonSlugIs404() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/lessons/does-not-exist-lesson")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(result)).isEqualTo("LESSON_NOT_FOUND");
  }

  @Test
  void mindMapReadReturnsTheRootTree() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/tracks/" + SEED_TRACK_SLUG + "/mindmap")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("root").path("id").asString()).isEqualTo("root");
    assertThat(body.path("locale").asString()).isEqualTo("en");
  }

  @Test
  void mindMapIsAlwaysReportedAsAFallbackForANonEnglishLocale() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/tracks/" + SEED_TRACK_SLUG + "/mindmap?locale=tr"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("locale").asString()).isEqualTo("en");
    assertThat(body.path("requested_locale").asString()).isEqualTo("tr");
    assertThat(body.path("is_fallback").asBoolean()).isTrue();
  }

  @Test
  void anUntranslatedTrackFallsBackToEnglishWithIsFallbackTrue() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/tracks/" + SEED_TRACK_SLUG + "?locale=de")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("locale").asString()).isEqualTo("en");
    assertThat(body.path("requested_locale").asString()).isEqualTo("de");
    assertThat(body.path("is_fallback").asBoolean()).isTrue();
  }

  @Test
  void anUnsupportedLocaleQueryParameterIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks?locale=xx")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("UNSUPPORTED_LOCALE");
  }

  @Test
  void acceptLanguageHeaderIsHonouredWhenNoQueryParameterIsGiven() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/tracks/" + SEED_TRACK_SLUG)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(result).path("requested_locale").asString()).isEqualTo("tr");
  }

  @Test
  void blogPostsListReturnsAnEmptyPageWhenThereAreNonePublished() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/blog/posts")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.path("items").isArray()).isTrue();
    assertThat(body.path("page").asInt()).isEqualTo(0);
  }

  @Test
  void unknownBlogPostSlugIs404() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/blog/posts/does-not-exist-post")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(errorCode(result)).isEqualTo("BLOG_POST_NOT_FOUND");
  }

  @Test
  void aPageSizeAboveTheCeilingIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks?size=101")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("PAGE_SIZE_EXCEEDED");
  }

  @Test
  void anUnknownSortFieldIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/tracks?sort=not_a_field,asc")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(errorCode(result)).isEqualTo("INVALID_SORT_FIELD");
  }
}
