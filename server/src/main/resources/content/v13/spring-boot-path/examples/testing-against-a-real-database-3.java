// Spring Boot 4.1, Testcontainers 2.0. The container as a Spring bean, so the
// framework starts it before every other bean and stops it after them, and the
// application's own datasource points at it. Compiled against the project's
// dependencies; not run here, because it only means anything when a JUnit engine
// builds the application context around it.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootApplication
class DemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}

@TestConfiguration(proxyBeanMethods = false)
class ContainerConfiguration {

  /**
   * Declared as a bean rather than through the JUnit extension. A container bean is
   * started before every other bean and stopped after all of them are destroyed, and
   * it survives across test classes that share a cached application context; a
   * container managed by the JUnit extension is stopped when its class finishes,
   * which leaves a cached context holding beans pointing at a database that is gone.
   *
   * <p>{@code @ServiceConnection} supplies the JDBC url, user and password to the
   * auto-configured datasource. There is deliberately no {@code spring.datasource.*}
   * property anywhere in the test configuration: with one, a test run could reach a
   * developer's own database and migrate or truncate it.
   *
   * <p>The image tag pins the same major version the application is deployed on.
   */
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  }
}

@Import(ContainerConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class DatabaseBackedTest {
  // Subclasses inherit the container, the context and the profile. Each one cleans
  // up the rows it created: the context, and therefore the database behind it, is
  // shared with every other class that asks for the same configuration.
}
