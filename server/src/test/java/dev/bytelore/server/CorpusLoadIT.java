package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The corpus migration, end to end against a real Postgres: it was discovered, it ran, and the rows
 * it wrote are the rows the fixture describes.
 *
 * <p>The first assertion is the one that is easy to leave out and expensive to leave out. A Java
 * migration that Flyway never discovers behaves exactly like a passing build: nothing fails,
 * nothing is logged, and the content simply is not there. Asserting that the version appears in the
 * history table is what tells the difference between "the loader ran and found nothing to do" and
 * "the loader was never called".
 *
 * <p>The suite points the loader at a fixture corpus rather than the real one -- see {@code
 * application-test.yml} -- so these numbers describe the fixture. Every assertion is scoped to the
 * fixture track's own identifier rather than counting whole tables, because the Testcontainer is
 * shared across test classes through Spring's context cache and a global count would depend on
 * which classes happened to run first.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CorpusLoadIT {

  private static final UUID FIXTURE_TRACK_ID =
      UUID.fromString("019205a0-1000-7000-8000-000000000032");
  private static final UUID FIXTURE_MIND_MAP_ID =
      UUID.fromString("019205a0-4000-7000-8000-000000000032");

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  /** The migration was discovered by Flyway and applied. */
  @Test
  void theCorpusMigrationRan() {
    List<Map<String, Object>> rows =
        jdbc()
            .queryForList(
                "SELECT description, type, success FROM flyway_schema_history WHERE version = '13'");

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("description")).isEqualTo("load content corpus");
    assertThat(rows.getFirst().get("type")).isEqualTo("JDBC");
    assertThat(rows.getFirst().get("success")).isEqualTo(Boolean.TRUE);
  }

  /**
   * A loaded track is unpublished, and stays that way.
   *
   * <p>The public read paths serve only published tracks, so a freshly loaded corpus is invisible
   * in the product while somebody reviews it. Publication is a one-line migration per track written
   * by a person, and that commit is the approval -- nothing automatically produced reaches a reader
   * without a human saying so.
   */
  @Test
  void theCorpusTrackIsLoadedUnpublished() {
    Boolean published =
        jdbc()
            .queryForObject(
                "SELECT published FROM tracks WHERE id = ?", Boolean.class, FIXTURE_TRACK_ID);

    assertThat(published).isFalse();
  }

  @Test
  void theWholeTrackStructureWasWritten() {
    JdbcTemplate jdbc = jdbc();

    assertThat(
            jdbc.queryForObject(
                "SELECT slug FROM tracks WHERE id = ?", String.class, FIXTURE_TRACK_ID))
        .isEqualTo("fixture-path");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM modules WHERE track_id = ?", Integer.class, FIXTURE_TRACK_ID))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM lessons l JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ?",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isEqualTo(9);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM code_examples e"
                    + " JOIN lessons l ON l.id = e.lesson_id"
                    + " JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ?",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isEqualTo(18);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM mind_maps WHERE track_id = ?",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isEqualTo(1);
  }

  /** The pinned version a track teaches lives in the description's trailing sentence. */
  @Test
  void theTaughtVersionIsStoredWithTheDescription() {
    String description =
        jdbc()
            .queryForObject(
                "SELECT description FROM tracks WHERE id = ?", String.class, FIXTURE_TRACK_ID);

    assertThat(description).endsWith("Teaches Python 3.13.");
  }

  /**
   * The identifiers are the ones the block scheme allocates, so a developer machine, continuous
   * integration and a deployment all address the same row.
   */
  @Test
  void everyIdentifierComesFromTheTracksAllocatedBlock() {
    JdbcTemplate jdbc = jdbc();

    assertThat(
            jdbc.queryForList(
                "SELECT id::text FROM modules WHERE track_id = ? ORDER BY display_order",
                String.class,
                FIXTURE_TRACK_ID))
        .containsExactly(
            "019205a0-2000-7000-8000-000000003201",
            "019205a0-2000-7000-8000-000000003202",
            "019205a0-2000-7000-8000-000000003203");
    assertThat(
            jdbc.queryForList(
                "SELECT l.id::text FROM lessons l JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ? ORDER BY m.display_order, l.display_order",
                String.class,
                FIXTURE_TRACK_ID))
        .startsWith(
            "019205a0-3000-7000-8000-000000320101",
            "019205a0-3000-7000-8000-000000320102",
            "019205a0-3000-7000-8000-000000320103")
        .hasSize(9);
  }

  /**
   * The mind map is derived from the track: root, three modules, nine lessons, and the concepts the
   * lessons declare beneath them.
   */
  @Test
  void theMindMapWasDerivedFromTheTrackStructure() {
    JdbcTemplate jdbc = jdbc();

    String root =
        jdbc.queryForObject(
            "SELECT root::text FROM mind_maps WHERE id = ?", String.class, FIXTURE_MIND_MAP_ID);
    assertThat(root).contains("The Fixture Path", "Values and Types", "What a value holds");

    Integer lessonNodes =
        jdbc.queryForObject(
            "SELECT count(*) FROM mind_maps m,"
                + " jsonb_array_elements(m.root -> 'children') AS module,"
                + " jsonb_array_elements(module -> 'children') AS lesson"
                + " WHERE m.id = ?",
            Integer.class,
            FIXTURE_MIND_MAP_ID);
    assertThat(lessonNodes).isEqualTo(9);

    Integer danglingLessonReferences =
        jdbc.queryForObject(
            "SELECT count(*) FROM mind_maps m,"
                + " jsonb_array_elements(m.root -> 'children') AS module,"
                + " jsonb_array_elements(module -> 'children') AS lesson"
                + " WHERE m.id = ?"
                + " AND NOT EXISTS ("
                + "   SELECT 1 FROM lessons l JOIN modules md ON md.id = l.module_id"
                + "    WHERE l.id = (lesson ->> 'lesson_id')::uuid AND md.track_id = m.track_id)",
            Integer.class,
            FIXTURE_MIND_MAP_ID);
    assertThat(danglingLessonReferences).isZero();
  }

  /**
   * The loader wrote no digest, and startup filled one in.
   *
   * <p>A digest computed in a migration would be a second implementation of the packaging rules,
   * living somewhere the determinism tests cannot reach. The loader leaves the three package
   * columns null; the repackaging that runs at startup builds the canonical bytes and hashes them,
   * which is what makes a freshly loaded corpus downloadable without an operator running anything.
   */
  @Test
  void theLoaderWroteNoDigestAndStartupFilledThemIn() {
    JdbcTemplate jdbc = jdbc();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM lessons l JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ? AND l.sha256 IS NULL",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM mind_maps WHERE id = ? AND sha256 IS NOT NULL",
                Integer.class,
                FIXTURE_MIND_MAP_ID))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT content_version FROM lessons WHERE slug = 'what-a-value-holds'",
                Integer.class))
        .isEqualTo(1);
  }

  /** Bodies and listings are stored with LF endings, whatever the checkout produced. */
  @Test
  void nothingTheLoaderWroteCarriesACarriageReturn() {
    JdbcTemplate jdbc = jdbc();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM lessons l JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ? AND position(chr(13) in l.body_markdown) > 0",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM code_examples e"
                    + " JOIN lessons l ON l.id = e.lesson_id"
                    + " JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ? AND position(chr(13) in e.code) > 0",
                Integer.class,
                FIXTURE_TRACK_ID))
        .isZero();
  }

  /** An unpublished track is invisible to the public list, so a corpus under review cannot leak. */
  @Test
  void theCorpusTrackIsNotVisibleToThePublicTrackList() {
    List<String> publishedSlugs =
        jdbc().queryForList("SELECT slug FROM tracks WHERE published = true", String.class);

    assertThat(publishedSlugs).doesNotContain("fixture-path");
  }
}
