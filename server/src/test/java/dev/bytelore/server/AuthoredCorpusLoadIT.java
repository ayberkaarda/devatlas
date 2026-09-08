package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The corpus that actually ships, loaded by the real migration into a real Postgres, and then swept
 * by {@link ContentValidationSweep} exactly as the seeded content is.
 *
 * <p>This class exists because of a gap rather than a feature. The whole argument for loading
 * content through a Java migration is that content the administration API would refuse cannot get
 * in: the loader applies the same predicates, so a bad lesson fails the migration, and a failed
 * migration fails the build. That argument was true of the fixture corpus and unverified for the
 * one that ships -- the rest of the suite points the loader at a fixture, deliberately, so nothing
 * anywhere proved that the authored corpus could even be loaded. A claim that only holds for the
 * content nobody reads is not the claim that was being made.
 *
 * <p>What is asserted is mostly <em>not</em> row counts. The counts are here to catch a load that
 * silently wrote half of what it read; the assertion that carries the weight is inherited -- every
 * body through the markdown validator, every caption and every mind map label through the
 * plain-text validator, every listing against the closed language set, every mind map within its
 * structural ceilings. That sweep is written once and pointed at two databases.
 *
 * <p><strong>Why this class has a database of its own.</strong> Flyway records a migration by
 * version, not by what it read. The rest of the suite shares one container through Spring's context
 * cache, and by the time anything here ran, version 13 would already be recorded there as applied
 * from the fixture directory -- so pointing a second context at the real corpus would change
 * nothing at all: Flyway would see 13 in the history table and skip it, and this class would sweep
 * the fixture while appearing to sweep the corpus. That is the failure mode worth spending a
 * container on, because it looks exactly like success. A separate container gives an empty database
 * with an empty history table, which is the only state in which the loader is actually asked to do
 * the work. The first test below is what proves the separation happened rather than assuming it.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "bytelore.corpus.location=classpath:content/v13/")
class AuthoredCorpusLoadIT extends ContentValidationSweep {

  /**
   * Tracks the corpus directory holds: one {@code track.json} per subdirectory of {@code
   * content/v13}.
   */
  private static final int AUTHORED_TRACKS = 17;

  /** Modules the corpus declares: every {@code track.json} lists three. */
  private static final int AUTHORED_MODULES = 51;

  /** Lessons the corpus holds: one {@code lessons/*.md} body per lesson. */
  private static final int AUTHORED_LESSONS = 153;

  /**
   * Code listings the corpus holds: every file under a track's {@code examples/} directory except
   * the {@code .expected} companions, which are an expected-output field on a listing rather than a
   * listing of their own.
   */
  private static final int AUTHORED_CODE_LISTINGS = 458;

  /** One mind map is derived per track; the corpus does not author them by hand. */
  private static final int DERIVED_MIND_MAPS = 17;

  /**
   * A Postgres of this class's own, deliberately not the one {@code TestcontainersConfiguration}
   * hands to every other suite. Declaring it here rather than importing that class is what gives
   * this class a distinct Spring context, and therefore a distinct container with an empty Flyway
   * history.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class OwnDatabase {

    @Bean
    @ServiceConnection
    PostgreSQLContainer authoredCorpusPostgres() {
      return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
  }

  /**
   * The migration ran here, against a database that had never seen it.
   *
   * <p>Two halves, and the second is the one that matters. That version 13 is in the history table
   * only says a migration with that version was applied at some point in this database's life. What
   * distinguishes a real load from the shared container's is the content: the fixture track is the
   * one thing that would certainly be present had this class quietly connected to the database the
   * rest of the suite uses, and the authored tracks are the thing that would certainly be absent.
   */
  @Test
  void theCorpusMigrationRanAgainstADatabaseOfItsOwn() {
    List<Map<String, Object>> history =
        jdbc()
            .queryForList(
                "SELECT description, type, success FROM flyway_schema_history WHERE version = '13'");
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().get("description")).isEqualTo("load content corpus");
    assertThat(history.getFirst().get("type")).isEqualTo("JDBC");
    assertThat(history.getFirst().get("success")).isEqualTo(Boolean.TRUE);

    List<String> slugs = jdbc().queryForList("SELECT slug FROM tracks ORDER BY slug", String.class);
    assertThat(slugs).doesNotContain("fixture-path");
    assertThat(slugs).contains("angular-path", "python-path", "rust-path");
    assertThat(slugs).hasSize(AUTHORED_TRACKS);
  }

  /**
   * Everything the corpus holds on disk became a row.
   *
   * <p>The numbers are the shape of the authored corpus, counted from the files themselves rather
   * than from a previous run of this test: a track per {@code track.json}, three modules per track
   * as each declares, a lesson per markdown body, a listing per example file that is not an
   * expected-output companion, and a derived mind map per track. Adding content is meant to move
   * these numbers, and that is the point -- a loader that read a track and wrote most of it would
   * otherwise pass.
   *
   * <p>Lessons are counted excluding the soft-deleted one. The sample seed's four Angular lessons
   * predate the corpus, three of their identifiers are rewritten in place by the authored track,
   * and the fourth is retired by a later migration rather than deleted, because a completion
   * recorded against it is a fact about a person's history. That retired row is still in the table
   * and is not part of the corpus.
   */
  @Test
  void everyAuthoredFileBecameARow() {
    JdbcTemplate jdbc = jdbc();

    Map<String, Integer> loaded = new LinkedHashMap<>();
    loaded.put("tracks", jdbc.queryForObject("SELECT count(*) FROM tracks", Integer.class));
    loaded.put("modules", jdbc.queryForObject("SELECT count(*) FROM modules", Integer.class));
    loaded.put(
        "lessons",
        jdbc.queryForObject(
            "SELECT count(*) FROM lessons WHERE deleted_at IS NULL", Integer.class));
    loaded.put(
        "code listings", jdbc.queryForObject("SELECT count(*) FROM code_examples", Integer.class));
    loaded.put("mind maps", jdbc.queryForObject("SELECT count(*) FROM mind_maps", Integer.class));

    // Compared as one map rather than one assertion per row type, so that a load which wrote part
    // of what it read reports the whole shape it produced instead of stopping at the first number
    // that moved.
    assertThat(loaded)
        .isEqualTo(
            Map.of(
                "tracks", AUTHORED_TRACKS,
                "modules", AUTHORED_MODULES,
                "lessons", AUTHORED_LESSONS,
                "code listings", AUTHORED_CODE_LISTINGS,
                "mind maps", DERIVED_MIND_MAPS));
  }

  /**
   * Nothing in a freshly migrated database is on the public surface.
   *
   * <p>A corpus track arrives unpublished and is published by a one-line migration a person commits
   * after reading it, so a database that has only ever had migrations run against it should serve
   * nobody anything. The sample seed's Angular track is the interesting row here: it was created
   * published, when it held four short lessons written to give the read endpoints something to
   * serve, and the corpus replaced its content with nine lessons nobody had read. This is the
   * assertion that would notice if the withdrawal were ever dropped.
   */
  @Test
  void aFreshlyMigratedDatabasePublishesNothing() {
    assertThat(
            jdbc()
                .queryForList(
                    "SELECT slug FROM tracks WHERE published = true ORDER BY slug", String.class))
        .isEmpty();
  }
}
