package dev.devatlas.server;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  // Pinned to the same major/minor as the dev docker-compose service
  // (postgres:16-alpine, see docker-compose.yml) so behaviour tested here
  // matches what actually runs locally and in production.
  //
  // PostgreSQLContainer is not generic in testcontainers 2.x (the BOM pulled
  // in by spring-boot-starter-parent 4.1.1) — unlike 1.x, no <?> here.
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  }
}
