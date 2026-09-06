package dev.bytelore.server.migration;

import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first administrator.
 *
 * <p>This is a Java migration rather than a SQL one for a single reason: the password must come
 * from the environment and be hashed before it touches the database. A SQL file cannot read an
 * environment variable, and the alternative -- a literal hash checked into the migration -- would
 * put a working credential into the repository's history permanently, where no later commit can
 * remove it.
 *
 * <p>Missing configuration is a hard failure, not a warning. A silent skip would record the
 * migration as applied and leave a deployment with no way in and no way to fix it short of a
 * database session; failing here leaves the transaction rolled back, so setting the variable and
 * starting again is all that is needed.
 */
@Component
public class V6__seed_admin_user extends BaseJavaMigration {

  private static final UUID ADMIN_ID = UUID.fromString("019205a0-0000-7000-8000-000000000001");

  private final String adminEmail;
  private final String adminPassword;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public V6__seed_admin_user(
      @Value("${bytelore.seed.admin-email:}") String adminEmail,
      @Value("${bytelore.seed.admin-password:}") String adminPassword,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void migrate(Context context) throws Exception {
    if (adminEmail == null || adminEmail.isBlank()) {
      throw new IllegalStateException(
          "bytelore.seed.admin-email is not set. Supply the administrator's address through the"
              + " environment before the first start.");
    }
    if (adminPassword == null || adminPassword.isBlank()) {
      throw new IllegalStateException(
          "bytelore.seed.admin-password is not set. Supply the initial administrator password"
              + " through the environment before the first start; it is never stored in source"
              + " control and there is no default.");
    }

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);

    String sql =
        """
        INSERT INTO users (id, email, password_hash, role, locale, theme, enabled,
                           created_at, updated_at)
        VALUES (?, ?, ?, 'ADMIN', 'en', 'SYSTEM', true, ?, ?)
        """;
    try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
      statement.setObject(1, ADMIN_ID);
      statement.setString(2, adminEmail.trim().toLowerCase(Locale.ROOT));
      statement.setString(3, passwordEncoder.encode(adminPassword));
      statement.setObject(4, timestamp);
      statement.setObject(5, timestamp);
      statement.executeUpdate();
    }
  }
}
