package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Public content read (§5.2): anonymous access, published-only visibility, locale resolution and
 * the {@code is_fallback} bookkeeping.
 *
 * <p>The content read here is the fixed sample content the seed migration inserts -- the {@code
 * angular-path} track, its two modules, its lessons and its mind map -- because a track with real
 * modules, a real mind map and a real translation is what the locale and fallback assertions need
 * to mean anything.
 *
 * <p><strong>The track is published by this class, not found published.</strong> Every assertion
 * below is a statement about an endpoint: that a published track is listed, that its detail carries
 * modules without lesson bodies, that a lesson read returns a body, that an untranslated locale
 * falls back. None of them is a statement about which content an editor decided to publish, and a
 * suite that inherited that decision would go red the day somebody withdrew a track for review --
 * which is a correct editorial act, not a regression in the read API. So the flag is arranged here
 * and restored afterwards, and the database's own publication state stays out of it.
 *
 * <p>Restoring matters as much as setting: the Testcontainer is shared across test classes through
 * Spring's context cache, so a track left published would be published for every class that runs
 * afterwards.
 */
class PublicContentReadIT extends ContentApiTestSupport {

  private static final String SEED_TRACK_SLUG = "angular-path";
  private static final String SEED_LESSON_SLUG = "signals-and-reactivity";

  /**
   * Lessons the seed track shows a reader: the seed inserts four and a later migration retires the
   * one the rewritten track no longer covers, and every read path scopes to lessons that have not
   * been soft-deleted.
   */
  private static final int SEED_TRACK_VISIBLE_LESSONS = 3;

  private static final int SEED_TRACK_MODULES = 2;

  private boolean previouslyPublished;

  @BeforeEach
  void publishTheTrackUnderTest() {
    previouslyPublished = setTrackPublished(SEED_TRACK_ID, true);
  }

  @AfterEach
  void restorePublicationState() {
    setTrackPublished(SEED_TRACK_ID, previouslyPublished);
  }

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
        assertThat(item.path("module_count").asInt()).isEqualTo(SEED_TRACK_MODULES);
        assertThat(item.path("lesson_count").asInt()).isEqualTo(SEED_TRACK_VISIBLE_LESSONS);
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
    assertThat(body.path("modules")).hasSize(SEED_TRACK_MODULES);
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
