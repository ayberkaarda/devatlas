package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6");
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

  /** The seed content and the seeded administrator are both present. */
  @Test
  void seedDataIsPresent() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tracks", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM modules", Integer.class))
        .isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons", Integer.class))
        .isEqualTo(4);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM mind_maps", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class))
        .isEqualTo(1);
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
