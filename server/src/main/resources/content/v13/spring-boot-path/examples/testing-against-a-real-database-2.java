// Spring Boot 4.1, Testcontainers 2.0, AssertJ. A green test over a real database
// that proves nothing, beside the same test asserting on the row instead.
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.assertj.core.api.Assertions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class LyingFixtureDemo {

  record Track(String slug, String status) {}

  /** The code under test. The UPDATE matches nothing, and nobody checks. */
  static class TrackArchiver {
    private final Connection connection;

    TrackArchiver(Connection connection) {
      this.connection = connection;
    }

    Track archive(Track track) throws SQLException {
      try (PreparedStatement statement =
          connection.prepareStatement("UPDATE tracks SET status = 'ARCHIVED' WHERE id = ?")) {
        // The column is `slug`; `id` is a different column holding a different value.
        statement.setString(1, track.slug());
        statement.executeUpdate();
      }
      return new Track(track.slug(), "ARCHIVED");
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  static String storedStatus(Connection connection, String slug) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT status FROM tracks WHERE slug = ?")) {
      statement.setString(1, slug);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : null;
      }
    }
  }

  static void runTest(String label, Check check) {
    try {
      check.run();
      System.out.println(label + ": PASSED");
    } catch (AssertionError failed) {
      System.out.println(label + ": FAILED");
      System.out.println("   " + failed.getMessage().replaceAll("\\R", " ").replaceAll(" +", " ").trim());
    } catch (Exception broken) {
      System.out.println(label + ": ERROR " + broken.getClass().getSimpleName());
    }
  }

  interface Check {
    void run() throws Exception;
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    postgres.start();
    try (Connection connection =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            """
            CREATE TABLE tracks (
              id     text PRIMARY KEY,
              slug   text NOT NULL UNIQUE,
              status text NOT NULL
            )
            """);
        statement.execute(
            "INSERT INTO tracks (id, slug, status) VALUES ('t-02', 'spring-boot-path', 'ACTIVE')");
      }

      TrackArchiver archiver = new TrackArchiver(connection);
      Track fixture = new Track("spring-boot-path", "ACTIVE");

      // The assertion reads the value the method under test put in its own return
      // value. It would pass against an empty database, or no database at all.
      runTest(
          "asserting on what the method returned",
          () -> {
            Track archived = archiver.archive(fixture);
            Assertions.assertThat(archived.status()).isEqualTo("ARCHIVED");
          });

      // The same call, asked what the database now holds.
      runTest(
          "asserting on the stored row       ",
          () -> {
            archiver.archive(fixture);
            Assertions.assertThat(storedStatus(connection, "spring-boot-path")).isEqualTo("ARCHIVED");
          });
    } finally {
      postgres.stop();
    }
  }
}
