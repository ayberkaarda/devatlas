package dev.bytelore.server.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} and supplies the ShedLock {@link LockProvider} backing the blog
 * pipeline's per-source lock (§5.7 of the REST contract).
 *
 * <p>The lock provider is Postgres-backed, against the {@code shedlock} table created in {@code
 * V1__shedlock.sql} -- no Redis, per the "Just Postgres" stack rule. {@code usingDbTime()} asks the
 * provider to read the lock's expiry from the database's own clock rather than the application
 * server's, so lock arithmetic is correct even if the two clocks drift.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}
