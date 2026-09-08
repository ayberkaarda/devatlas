// Spring Boot 4.1, Testcontainers 2.0. A throwaway PostgreSQL 16 for the length of
// one program: the constraints under test are the ones production will enforce.
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class RealDatabaseDemo {

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    // Pinned to the same major version the application runs against. An unpinned
    // tag makes the test suite's meaning depend on the day it was run.
    // In Testcontainers 2.x PostgreSQLContainer is no longer generic; the 1.x
    // `new PostgreSQLContainer<>(...)` does not compile here.
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    postgres.start();
    try (Connection connection =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            """
            CREATE TABLE lessons (
              id            uuid PRIMARY KEY,
              slug          text NOT NULL,
              difficulty    text NOT NULL,
              created_at    timestamptz NOT NULL DEFAULT now(),
              CONSTRAINT lessons_slug_unique UNIQUE (slug),
              CONSTRAINT lessons_difficulty_known
                CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
            )
            """);
      }

      insert(connection, "019205a0-3000-7000-8000-000000020901", "testing-against-a-real-database", "ADVANCED");
      System.out.println("row inserted");

      try (Statement statement = connection.createStatement();
          ResultSet rows =
              statement.executeQuery("SELECT slug, created_at IS NOT NULL AS stamped FROM lessons")) {
        while (rows.next()) {
          // The column was never written by this program. The database filled it.
          System.out.println("  slug=" + rows.getString("slug") + " created_at set by the database=" + rows.getBoolean("stamped"));
        }
      }

      // A value the Java enum would have accepted, refused by the schema.
      report(
          () ->
              insert(
                  connection,
                  "019205a0-3000-7000-8000-000000020902",
                  "another-lesson",
                  "INTRODUCTORY"));
      // A duplicate slug, refused by the unique index.
      report(
          () ->
              insert(
                  connection,
                  "019205a0-3000-7000-8000-000000020903",
                  "testing-against-a-real-database",
                  "BEGINNER"));
    } finally {
      postgres.stop();
    }
  }

  static void insert(Connection connection, String id, String slug, String difficulty)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO lessons (id, slug, difficulty) VALUES (?::uuid, ?, ?)")) {
      statement.setString(1, id);
      statement.setString(2, slug);
      statement.setString(3, difficulty);
      statement.executeUpdate();
    }
  }

  interface Write {
    void run() throws SQLException;
  }

  static void report(Write write) {
    try {
      write.run();
      System.out.println("  accepted, which should not happen");
    } catch (PSQLException refused) {
      System.out.println("  refused by the database: " + refused.getServerErrorMessage().getConstraint());
    } catch (SQLException other) {
      System.out.println("  refused: " + other.getSQLState());
    }
  }
}
