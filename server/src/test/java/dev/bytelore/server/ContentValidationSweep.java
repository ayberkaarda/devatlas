package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.common.TextNormalizer;
import dev.bytelore.server.corpus.CorpusLanguages;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The second layer: after the whole migration chain has run, every row in the content tables is put
 * back through the predicates that were supposed to have kept it out if it were bad.
 *
 * <p>The corpus loader applies them on the way in, and the administration API applies them on its
 * own way in, so in principle this can only pass. It exists because "in principle" is exactly the
 * situation in which nobody looks: the original sample seed was written as SQL and went nowhere
 * near the write boundary, so its rows had never been checked by anything at all. Whatever a later
 * migration inserts by hand will be in the same position, and this is what notices.
 *
 * <p>The sweep is over every row rather than over a list of rows somebody thought worth checking,
 * because the next unvalidated row will be one nobody predicted.
 *
 * <p>What is applied is the safety predicates -- the markdown validator for a body, the plain-text
 * validator for anything a client renders by interpolation, the closed language set for a listing,
 * and the mind map's structural ceilings. The corpus format's authoring rules, such as its word
 * limits, are deliberately <strong>not</strong> applied: they are a rule about what makes a good
 * lesson, they belong to the corpus, and the sample seed's four short lessons predate them.
 *
 * <p><strong>Why this is a base class and not a test class.</strong> The sweep is written against
 * whatever the connected database happens to hold, so it is worth exactly as much as the content it
 * is pointed at. It is pointed at two different databases by two subclasses: one holding the sample
 * seed and the small fixture corpus the suite loads everywhere else, and one holding the corpus
 * that actually ships. Neither could stand in for the other -- the fixture is the only content that
 * can be deliberately broken to watch a refusal, and the shipped corpus is the only content a
 * reader will ever see -- and writing the sweep twice would let the two copies drift.
 *
 * <p>Subclasses supply the Spring context and therefore the database. This class deliberately
 * carries no context annotations of its own, so that a subclass cannot silently inherit a database
 * it did not mean to connect to.
 */
abstract class ContentValidationSweep {

  private static final int MAX_MIND_MAP_DEPTH = 8;
  private static final int MAX_MIND_MAP_NODES = 500;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private DataSource dataSource;

  protected JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  /** Every lesson body in the database would be accepted by the write boundary today. */
  @Test
  void everyLessonBodyPassesTheMarkdownValidator() {
    List<Map<String, Object>> lessons =
        jdbc().queryForList("SELECT id, slug, body_markdown FROM lessons ORDER BY slug");

    assertThat(lessons).isNotEmpty();
    for (Map<String, Object> lesson : lessons) {
      String slug = (String) lesson.get("slug");
      String body = (String) lesson.get("body_markdown");
      assertThatCode(() -> MarkdownSanitizer.validateMarkdown(body, "body of lesson " + slug))
          .as("body of lesson '%s'", slug)
          .doesNotThrowAnyException();
    }
  }

  /**
   * Every stored body is already in its normalized form.
   *
   * <p>Normalization happens once, at the write boundary, so the stored bytes, the hashed bytes and
   * the served bytes are the same bytes. A row that is not in normal form would hash to something
   * the next writer of that row would not reproduce.
   */
  @Test
  void everyStoredBodyIsAlreadyNormalized() {
    for (Map<String, Object> lesson :
        jdbc().queryForList("SELECT slug, body_markdown FROM lessons")) {
      String slug = (String) lesson.get("slug");
      String body = (String) lesson.get("body_markdown");
      assertThat(body).as("body of lesson '%s'", slug).isEqualTo(TextNormalizer.normalize(body));
    }
    for (Map<String, Object> translation :
        jdbc()
            .queryForList(
                "SELECT entity_type, entity_id, locale, title, body FROM content_translations")) {
      String where =
          "%s %s (%s)"
              .formatted(
                  translation.get("entity_type"),
                  translation.get("entity_id"),
                  translation.get("locale"));
      assertThat((String) translation.get("title"))
          .as("title of " + where)
          .isEqualTo(TextNormalizer.normalize((String) translation.get("title")));
      String body = (String) translation.get("body");
      if (body != null) {
        assertThat(body).as("body of " + where).isEqualTo(TextNormalizer.normalize(body));
      }
    }
  }

  /** Translated bodies are markdown too, and are held to the same predicate as the English ones. */
  @Test
  void everyTranslatedBodyPassesTheMarkdownValidator() {
    for (Map<String, Object> translation :
        jdbc()
            .queryForList(
                "SELECT entity_type, entity_id, locale, body FROM content_translations"
                    + " WHERE body IS NOT NULL")) {
      String where =
          "%s %s (%s)"
              .formatted(
                  translation.get("entity_type"),
                  translation.get("entity_id"),
                  translation.get("locale"));
      String body = (String) translation.get("body");
      assertThatCode(() -> MarkdownSanitizer.validateMarkdown(body, where))
          .as(where)
          .doesNotThrowAnyException();
    }
  }

  /**
   * Every stored listing is in the closed language set, and every caption is plain text.
   *
   * <p>The caption half is stricter than the administration API, which normalizes a caption but
   * does not examine it. A caption is rendered by interpolation exactly as a mind map label is, so
   * the corpus format holds it to the plain-text predicate, and this sweep does the same for every
   * row however it arrived.
   */
  @Test
  void everyCodeExampleUsesASupportedLanguageAndAPlainTextCaption() {
    for (Map<String, Object> example :
        jdbc().queryForList("SELECT id, language, caption FROM code_examples")) {
      String id = String.valueOf(example.get("id"));
      assertThat(CorpusLanguages.SUPPORTED_LANGUAGES)
          .as("language of code example %s", id)
          .contains((String) example.get("language"));
      String caption = (String) example.get("caption");
      if (caption != null) {
        assertThatCode(() -> MarkdownSanitizer.validatePlainText(caption, "caption of " + id))
            .as("caption of code example %s", id)
            .doesNotThrowAnyException();
      }
    }
  }

  /**
   * Every stored mind map is one a client can render: plain-text labels, no repeated node, within
   * the depth and node ceilings, and no lesson reference that leaves its own track.
   */
  @Test
  void everyMindMapIsWellFormed() {
    List<Map<String, Object>> mindMaps =
        jdbc().queryForList("SELECT id, track_id, root::text AS root FROM mind_maps");

    assertThat(mindMaps).isNotEmpty();
    for (Map<String, Object> mindMap : mindMaps) {
      UUID id = (UUID) mindMap.get("id");
      UUID trackId = (UUID) mindMap.get("track_id");
      Set<UUID> lessonsOfTrack =
          new HashSet<>(
              jdbc()
                  .queryForList(
                      "SELECT l.id FROM lessons l JOIN modules m ON m.id = l.module_id"
                          + " WHERE m.track_id = ?",
                      UUID.class,
                      trackId));

      JsonNode root = JSON.readTree((String) mindMap.get("root"));
      List<String> problems = new ArrayList<>();
      walk(root, 1, new int[] {0}, new HashSet<>(), lessonsOfTrack, problems);
      assertThat(problems).as("mind map %s", id).isEmpty();
    }
  }

  private void walk(
      JsonNode node,
      int depth,
      int[] nodeCount,
      Set<String> seenNodeIds,
      Set<UUID> lessonsOfTrack,
      List<String> problems) {
    String id = node.path("id").asString();
    if (depth > MAX_MIND_MAP_DEPTH) {
      problems.add("node '%s' is at depth %d".formatted(id, depth));
      return;
    }
    if (++nodeCount[0] > MAX_MIND_MAP_NODES) {
      problems.add("more than %d nodes".formatted(MAX_MIND_MAP_NODES));
      return;
    }
    if (!seenNodeIds.add(id)) {
      problems.add("node id '%s' appears more than once".formatted(id));
    }

    String label = node.path("label").asString();
    if (label.isBlank()) {
      problems.add("node '%s' has a blank label".formatted(id));
    } else {
      try {
        MarkdownSanitizer.validatePlainText(label, "label of node '" + id + "'");
      } catch (RuntimeException e) {
        problems.add("node '%s': %s".formatted(id, e.getMessage()));
      }
      if (!label.equals(TextNormalizer.normalize(label))) {
        problems.add("node '%s' has a label that is not normalized".formatted(id));
      }
    }

    JsonNode lessonId = node.path("lesson_id");
    if (!lessonId.isNull() && !lessonId.isMissingNode()) {
      UUID referenced = UUID.fromString(lessonId.asString());
      if (!lessonsOfTrack.contains(referenced)) {
        problems.add(
            "node '%s' references lesson %s, which is not in this track".formatted(id, referenced));
      }
    }

    for (JsonNode child : node.path("children")) {
      walk(child, depth + 1, nodeCount, seenNodeIds, lessonsOfTrack, problems);
    }
  }
}
