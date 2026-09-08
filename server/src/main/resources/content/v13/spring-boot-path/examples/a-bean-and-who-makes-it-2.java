// Spring Boot 4.1 (Spring Framework 7.0). Two beans that need each other.
// Constructor injection cannot build either one, and says so at startup.
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CircularDependencyDemo {

  // Each constructor demands the other collaborator before it can finish. There is
  // no order in which both can be built.
  static class Catalogue {
    Catalogue(Pricing pricing) {}
  }

  static class Pricing {
    Pricing(Catalogue catalogue) {}
  }

  // The same cycle expressed through fields. Both objects can be created empty and
  // filled afterwards, so the plain container below completes it -- and the design
  // flaw the constructor version reported at startup survives instead.
  //
  // A Spring Boot 4.1 application refuses this too: spring.main.allow-circular-
  // references defaults to false there, and the same BeanCurrentlyInCreationException
  // is thrown. The plain AnnotationConfigApplicationContext used here has no such
  // setting, which is what makes the difference visible in one program.
  static class FieldCatalogue {
    @Autowired FieldPricing pricing;
  }

  static class FieldPricing {
    @Autowired FieldCatalogue catalogue;
  }

  // Spring Boot's default logging back end is Logback. It is switched off here so
  // that the only output below is what this program prints itself; a real
  // application configures levels through properties instead.
  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) {
    quietLogging();

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(Catalogue.class, Pricing.class);
      context.refresh();
      System.out.println("constructor cycle: started, which should not happen");
    } catch (RuntimeException failure) {
      System.out.println("constructor cycle refused: " + failure.getClass().getSimpleName());
      Throwable root = failure;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      System.out.println("  root cause: " + root.getClass().getSimpleName());
    }

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(FieldCatalogue.class, FieldPricing.class);
      context.refresh();
      FieldCatalogue catalogue = context.getBean(FieldCatalogue.class);
      System.out.println("field cycle: started");
      System.out.println("  catalogue sees pricing: " + (catalogue.pricing != null));
      System.out.println("  pricing sees catalogue: " + (catalogue.pricing.catalogue != null));
    }
  }
}
