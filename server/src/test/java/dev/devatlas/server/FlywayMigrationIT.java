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
 * Boots the full Spring context against a real Postgres 16 Testcontainer and confirms that Flyway
 * actually ran: exactly one migration applied, and it is V1 (shedlock), and it succeeded.
 *
 * <p>This is the evidence for the whole chain in this scaffold: Boot context startup + real
 * datasource + Flyway migration + schema validation (ddl-auto=validate would fail the context if
 * the shedlock entity/table disagreed, but no entity is mapped yet in this phase, so this test
 * checks the migration history table directly instead).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

  @Autowired private DataSource dataSource;

  @Test
  void appliesExactlyOneMigration() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    List<String> appliedVersions =
        jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY"
                + " installed_rank",
            String.class);

    assertThat(appliedVersions).containsExactly("1");
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
}
