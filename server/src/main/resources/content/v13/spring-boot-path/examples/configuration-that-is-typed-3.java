// Spring Boot 4.1. How a @ConfigurationProperties type is registered decides whether
// it is bound at all. The annotation on the type is not what does the binding.
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RegistrationDecidesBindingDemo {

  @ConfigurationProperties("bytelore.mirror")
  record MirrorProperties(String host, int port) {}

  // Registered as configuration properties. Spring Boot creates the instance itself
  // through the record's canonical constructor, filling it from the environment.
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(MirrorProperties.class)
  static class BoundApplication {}

  // Created by an ordinary bean method. The container calls this method, gets a
  // finished object back, and has nothing left to bind: constructor binding is not
  // available to beans the regular Spring mechanisms create.
  @Configuration(proxyBeanMethods = false)
  static class UnboundApplication {
    @Bean
    MirrorProperties mirrorProperties() {
      return new MirrorProperties("localhost", 0);
    }
  }

  static void start(String label, Class<?> configuration) {
    SpringApplication application = new SpringApplication(configuration);
    application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
    application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
    application.setDefaultProperties(
        Map.of(
            "bytelore.mirror.host", "mirror.example.org",
            "bytelore.mirror.port", "8443",
            "logging.level.root", "off"));
    try (ConfigurableApplicationContext context = application.run()) {
      MirrorProperties bound = context.getBean(MirrorProperties.class);
      System.out.println(label + ": " + bound.host() + ":" + bound.port());
    }
  }

  public static void main(String[] args) {
    start("@EnableConfigurationProperties", BoundApplication.class);
    start("plain @Bean method       ", UnboundApplication.class);
  }
}
