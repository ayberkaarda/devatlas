// Spring Boot 4.1. A record bound from properties: kebab-case keys, a Duration
// parsed from ISO-8601 text, a list, and a default that fills a missing key.
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class TypedConfigurationDemo {

  // A record has exactly one constructor, so constructor binding applies and no
  // extra annotation is needed. Every component is final; there is no setter with
  // which a later caller could change what startup decided.
  @ConfigurationProperties("bytelore.download")
  record DownloadProperties(
      @DefaultValue("3") int maxAttempts,
      @DefaultValue("PT5S") Duration retryDelay,
      List<String> mirrors,
      @DefaultValue("bytelore") String userAgent) {}

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DownloadProperties.class)
  static class DemoApplication {}

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(DemoApplication.class);
    // No servlet container, no banner and no startup logging, so that the output
    // below is only what this program prints.
    application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
    application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
    // Three of the four keys are supplied, in kebab-case rather than the record's
    // camelCase, and the fourth is left out entirely.
    application.setDefaultProperties(
        Map.of(
            "bytelore.download.max-attempts", "5",
            "bytelore.download.retry-delay", "PT2S",
            "bytelore.download.mirrors", "eu-west,eu-north",
            "logging.level.root", "off"));

    try (ConfigurableApplicationContext context = application.run()) {
      DownloadProperties properties = context.getBean(DownloadProperties.class);
      System.out.println("maxAttempts: " + properties.maxAttempts());
      System.out.println("retryDelay seconds: " + properties.retryDelay().toSeconds());
      System.out.println("mirrors: " + properties.mirrors());
      System.out.println("userAgent (defaulted): " + properties.userAgent());
      System.out.println("record is immutable: " + (properties.getClass().isRecord()));
    }
  }
}
