package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context against a real Postgres 16 Testcontainer and confirms that the
 * migration chain actually ran end to end.
 *
 * <p>This is the evidence for the whole scaffold: context startup, a real datasource, every Flyway
 * migration applied in order, and schema validation. Schema validation is not a separate assertion
 * here -- with {@code ddl-auto=validate}, the context this test boots would have failed to start if
 * any mapped entity disagreed with the table the migrations produced.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

  @Autowired private DataSource dataSource;

  @Test
  void appliesEveryMigrationInOrder() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    List<String> appliedVersions =
        jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY"
                + " installed_rank",
            String.class);

    assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
  }

  @Test
  void shedlockTableExists() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    Integer columnCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'shedlock'",
            Integer.class);

    assertThat(columnCount).isEqualTo(4);
  }

  @Test
  void everyDomainTableExists() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                + " ORDER BY table_name",
            String.class);

    assertThat(tables)
        .contains(
            "blog_posts",
            "code_examples",
            "content_translations",
            "lessons",
            "mind_maps",
            "modules",
            "pipeline_audit_log",
            "rate_limit_counters",
            "refresh_tokens",
            "source_updates",
            "tracks",
            "user_progress",
            "users",
            "whitelist_sources");
  }

  /**
   * No trigger and no stored function exists anywhere in the schema.
   *
   * <p>The rule this guards is narrow but load-bearing: content digests are computed in the service
   * layer, in the same transaction as the write they describe. A trigger or generated column that
   * computed one would put the computation somewhere the determinism tests cannot reach, and the
   * SQL available for it does not perform the text normalization the digest depends on at all. The
   * assertion is written against the whole schema rather than against a list of suspect names,
   * because the next such trigger will have a name nobody predicted.
   */
  @Test
  void schemaContainsNoTriggersOrFunctions() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    List<String> triggers =
        jdbcTemplate.queryForList(
            "SELECT trigger_name FROM information_schema.triggers"
                + " WHERE trigger_schema = 'public'",
            String.class);
    List<String> routines =
        jdbcTemplate.queryForList(
            "SELECT routine_name FROM information_schema.routines"
                + " WHERE routine_schema = 'public'",
            String.class);

    assertThat(triggers).isEmpty();
    assertThat(routines).isEmpty();
  }

  /**
   * The lesson slug index is partial. A plain unique index would let every soft-deleted lesson hold
   * its slug forever, so an editor who deleted a lesson could never recreate one at the same
   * address.
   */
  @Test
  void lessonSlugIndexIsPartial() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    String definition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_lessons_slug'", String.class);

    assertThat(definition).contains("UNIQUE").contains("WHERE (deleted_at IS NULL)");
  }

  /**
   * The seed content and the seeded administrator are both present.
   *
   * <p>Every assertion is scoped to the seeded track rather than counting whole tables. The
   * Testcontainer is shared across test classes through Spring's context cache, so any test that
   * inserts a track is also inserting into the database this one reads; a global {@code count(*)}
   * makes this test depend on which classes happened to run first, which differs between a
   * developer's machine and a clean CI checkout. Scoping the query removes the coupling entirely
   * instead of relying on other tests to tidy up after themselves.
   */
  @Test
  void seedDataIsPresent() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    UUID seedTrackId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tracks WHERE slug = 'angular-path'", UUID.class);
    assertThat(seedTrackId).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM modules WHERE track_id = ?", Integer.class, seedTrackId))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lessons l"
                    + " JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ?",
                Integer.class,
                seedTrackId))
        .isEqualTo(4);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mind_maps WHERE track_id = ?", Integer.class, seedTrackId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class))
        .isGreaterThanOrEqualTo(1);
  }

  /**
   * The seeded track carries a real translation, with a body that is actually there.
   *
   * <p>This is the fixture that makes translated content reachable from a fresh database. A
   * translation is a nested object inside the hashed package bytes and the thing the "not yet
   * translated" fallback is measured against, so seeded content that carried none would leave every
   * test running against it blind to that half of a package -- and a field no test ever populates
   * is a field whose absence looks exactly like success.
   */
  @Test
  void theSeededTrackCarriesATranslationWithABody() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    UUID seedTrackId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tracks WHERE slug = 'angular-path'", UUID.class);

    List<String> locales =
        jdbcTemplate.queryForList(
            "SELECT t.locale FROM content_translations t"
                + " JOIN lessons l ON l.id = t.entity_id"
                + " JOIN modules m ON m.id = l.module_id"
                + " WHERE t.entity_type = 'LESSON' AND m.track_id = ?"
                + " AND t.body IS NOT NULL AND btrim(t.body) <> ''"
                + " ORDER BY t.locale",
            String.class,
            seedTrackId);

    assertThat(locales).contains("tr");
    assertThat(locales).hasSizeGreaterThanOrEqualTo(1);
  }

  /**
   * Seeded content carries a stored package once the application has started.
   *
   * <p>The seed migration inserts lessons and a mind map with their package columns null, because a
   * digest written by hand in SQL would be a second, unverifiable implementation of the packaging
   * rules. A later migration clears every stored package for the same reason, whenever the package
   * format changes. Both leave rows that no manifest may advertise until the service layer builds
   * their bytes -- so the assertion here is that startup actually does it, and that a fresh
   * database is therefore fully downloadable without an operator running anything by hand.
   */
  @Test
  void seededContentIsPackagedByTheTimeTheApplicationHasStarted() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    UUID seedTrackId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tracks WHERE slug = 'angular-path'", UUID.class);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lessons l"
                    + " JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ? AND l.sha256 IS NULL",
                Integer.class,
                seedTrackId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mind_maps WHERE track_id = ? AND sha256 IS NOT NULL",
                Integer.class,
                seedTrackId))
        .isEqualTo(1);
    // The three package columns move together: a digest with no bytes behind it is the state the
    // check constraints exist to make impossible.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lessons l"
                    + " JOIN modules m ON m.id = l.module_id"
                    + " WHERE m.track_id = ?"
                    + " AND (l.package_bytes IS NULL OR l.package_size_bytes IS NULL)",
                Integer.class,
                seedTrackId))
        .isZero();
  }

  /**
   * Seeded lesson bodies are stored with LF endings only. The seed writes them as escaped strings
   * precisely so that the line endings of a checkout cannot leak into stored content and change the
   * digest computed over it.
   */
  @Test
  void seededLessonBodiesContainNoCarriageReturns() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    Integer offending =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM lessons WHERE position(chr(13) in body_markdown) > 0",
            Integer.class);

    assertThat(offending).isZero();
  }
}
