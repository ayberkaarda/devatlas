// Spring Boot 4.1. @Validated on a @ConfigurationProperties type turns a bad value
// into a failure at startup instead of a surprise on the first request.
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;

class ValidatedConfigurationDemo {

  // Spring Boot validates a @ConfigurationProperties type when it carries Spring's
  // @Validated, using whatever JSR-303 implementation is on the classpath.
  @ConfigurationProperties("bytelore.auth")
  @Validated
  record AuthProperties(@Min(1) int accessTtlMinutes, @NotBlank String jwtSecret) {}

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AuthProperties.class)
  static class DemoApplication {}

  static void start(String label, Map<String, Object> properties) {
    SpringApplication application = new SpringApplication(DemoApplication.class);
    application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
    application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
    application.setDefaultProperties(properties);
    try (ConfigurableApplicationContext context = application.run()) {
      AuthProperties bound = context.getBean(AuthProperties.class);
      System.out.println(label + ": started, accessTtlMinutes=" + bound.accessTtlMinutes());
    } catch (RuntimeException failure) {
      Throwable root = failure;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      System.out.println(label + ": refused at startup by " + root.getClass().getSimpleName());
      if (root instanceof BindValidationException invalid) {
        // The default constraint message is rendered in the platform's locale, so
        // the field and the constraint code are what is printed here.
        List<String> reported = new ArrayList<>();
        for (ObjectError error : invalid.getValidationErrors().getAllErrors()) {
          String field = error instanceof FieldError fieldError ? fieldError.getField() : "(object)";
          reported.add("  " + field + " violates " + error.getCode());
        }
        Collections.sort(reported);
        reported.forEach(System.out::println);
      }
    }
  }

  public static void main(String[] args) {
    start(
        "valid",
        Map.of(
            "bytelore.auth.access-ttl-minutes", "15",
            "bytelore.auth.jwt-secret", "a-key-supplied-from-the-environment",
            "logging.level.root", "off"));
    start(
        "invalid",
        Map.of(
            "bytelore.auth.access-ttl-minutes", "0",
            "bytelore.auth.jwt-secret", "  ",
            "logging.level.root", "off"));
  }
}
