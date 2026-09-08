// Spring Boot 4.1 (Spring Framework 7.0). The default rollback rule, and the
// checked exception that commits everything written before it was thrown.
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class TransactionRollbackDemo {

  /** Records the outcome instead of connecting to a database. */
  static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    final List<String> events = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      events.add("begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      events.add("COMMIT");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      events.add("ROLLBACK");
    }
  }

  static class OutOfSeats extends RuntimeException {}

  /** A checked exception: the compiler forces callers to acknowledge it. */
  static class SeatLedgerUnavailable extends Exception {}

  static class Enrolments {
    @Transactional
    void failsUnchecked() {
      throw new OutOfSeats();
    }

    // Nothing here says "commit on failure", and yet that is what happens: the
    // default rule rolls back for RuntimeException and Error only.
    @Transactional
    void failsChecked() throws SeatLedgerUnavailable {
      throw new SeatLedgerUnavailable();
    }

    @Transactional(rollbackFor = SeatLedgerUnavailable.class)
    void failsCheckedWithRule() throws SeatLedgerUnavailable {
      throw new SeatLedgerUnavailable();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class DemoConfiguration {
    @Bean
    RecordingTransactionManager transactionManager() {
      return new RecordingTransactionManager();
    }

    @Bean
    Enrolments enrolments() {
      return new Enrolments();
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  interface Attempt {
    void run() throws Exception;
  }

  static void observe(RecordingTransactionManager manager, String label, Attempt attempt) {
    manager.events.clear();
    try {
      attempt.run();
    } catch (Exception expected) {
      // The exception still reaches the caller either way; only the outcome of the
      // transaction differs.
    }
    System.out.println(label + ": " + manager.events);
  }

  public static void main(String[] args) {
    quietLogging();

    try (var context = new AnnotationConfigApplicationContext(DemoConfiguration.class)) {
      RecordingTransactionManager manager = context.getBean(RecordingTransactionManager.class);
      Enrolments enrolments = context.getBean(Enrolments.class);

      observe(manager, "unchecked exception          ", enrolments::failsUnchecked);
      observe(manager, "checked exception            ", enrolments::failsChecked);
      observe(manager, "checked exception, rollbackFor", enrolments::failsCheckedWithRule);
    }
  }
}
