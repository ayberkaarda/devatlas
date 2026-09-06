package dev.bytelore.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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

    assertThat(appliedVersions)
        .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
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
   * Every seeded whitelist source is shaped so the pipeline can actually use it, except the one
   * seeded row V12 disables on purpose (see that migration): OpenJDK's GitHub tags ({@code
   * "jdk-26+14"}) carry no dot, so the extractor -- which requires one, like every other
   * whitelisted source's version shape -- can never produce a candidate from it. That row is
   * asserted disabled explicitly below, rather than silently excluded, so a future migration that
   * re-enables it without also fixing {@code verify_url_pattern} is not mistaken for a passing
   * test.
   *
   * <p>The database already refuses a non-https URL and a verify pattern with no placeholder, but
   * it cannot count placeholders, and a pattern with two of them would be filled twice and fetch a
   * URL nobody intended. Nor does it know that two rows pointing at the same feed would make the
   * pipeline fetch it twice per cycle and draft every item twice. The rows are selected by the
   * fixed identifier series the seed migrations use, not by counting the whole table: the pipeline
   * tests insert and delete throwaway sources in the same shared container, and a global count
   * would depend on which classes happened to run first.
   */
  @Test
  void seededWhitelistSourcesAreWellFormed() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    String disabledOpenJdkId = "019205a0-6000-7000-8000-000000000003";

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT id, name, feed_url, verify_url_pattern, enabled FROM whitelist_sources"
                + " WHERE id::text LIKE '019205a0-6000-7000-8000-%' ORDER BY id");

    assertThat(rows).hasSize(44);

    for (Map<String, Object> row : rows) {
      String id = row.get("id").toString();
      String name = (String) row.get("name");
      String feedUrl = (String) row.get("feed_url");
      String pattern = (String) row.get("verify_url_pattern");

      assertThat(name).as("name").isNotBlank().hasSizeLessThanOrEqualTo(120);
      assertThat(feedUrl)
          .as("feed_url of %s", name)
          .startsWith("https://")
          .doesNotContainAnyWhitespaces();
      assertThat(pattern)
          .as("verify_url_pattern of %s", name)
          .startsWith("https://")
          .doesNotContainAnyWhitespaces();
      assertThat(pattern.split(Pattern.quote("{version}"), -1))
          .as("exactly one {version} placeholder in %s", name)
          .hasSize(2);
      boolean expectedEnabled = !id.equals(disabledOpenJdkId);
      assertThat((Boolean) row.get("enabled"))
          .as("enabled flag of %s", name)
          .isEqualTo(expectedEnabled);
    }

    assertThat(rows.stream().map(row -> row.get("name")).distinct()).hasSize(rows.size());
    assertThat(rows.stream().map(row -> row.get("feed_url")).distinct()).hasSize(rows.size());
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
