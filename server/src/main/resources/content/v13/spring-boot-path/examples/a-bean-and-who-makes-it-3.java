// Spring Boot 4.1 (Spring Framework 7.0). When two beans fit one constructor
// parameter, the container refuses to guess -- and the fix is to say which.
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

class AmbiguousBeanDemo {

  interface Clock {
    String now();
  }

  record FixedClock(String stamp) implements Clock {
    public String now() {
      return stamp;
    }
  }

  record Report(Clock clock) {
    String describe() {
      return "report at " + clock.now();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Ambiguous {
    @Bean
    Clock buildClock() {
      return new FixedClock("2026-01-01T00:00:00Z");
    }

    @Bean
    Clock releaseClock() {
      return new FixedClock("2026-06-01T00:00:00Z");
    }

    @Bean
    Report report(Clock clock) {
      return new Report(clock);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Resolved {
    @Bean
    @Primary
    Clock buildClock() {
      return new FixedClock("2026-01-01T00:00:00Z");
    }

    @Bean
    Clock releaseClock() {
      return new FixedClock("2026-06-01T00:00:00Z");
    }

    // Named explicitly, so the @Primary default is deliberately not taken here.
    @Bean
    Report report(@Qualifier("releaseClock") Clock clock) {
      return new Report(clock);
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) {
    quietLogging();

    try (var context = new AnnotationConfigApplicationContext(Ambiguous.class)) {
      System.out.println("ambiguous: " + context.getBean(Report.class).describe());
    } catch (RuntimeException failure) {
      Throwable root = failure;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      System.out.println("ambiguous refused: " + root.getClass().getSimpleName());
    }

    try (var context = new AnnotationConfigApplicationContext(Resolved.class)) {
      System.out.println("qualified: " + context.getBean(Report.class).describe());
      System.out.println("plain Clock lookup takes the primary: " + context.getBean(Clock.class).now());
    }
  }
}
